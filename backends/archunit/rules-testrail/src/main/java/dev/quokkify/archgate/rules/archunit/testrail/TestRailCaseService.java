package dev.quokkify.archgate.rules.archunit.testrail;

import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.cases.archunit.CaseIndex;
import dev.quokkify.archgate.cases.archunit.CaseQuery;
import dev.quokkify.archgate.cases.archunit.CaseService;
import dev.quokkify.archgate.cases.archunit.CaseServiceUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Looks up linked cases in one TestRail project through its REST API. The server, the login and the project come from
 * the variables {@value #URL}, {@value #USER}, {@value #PASSWORD} and {@value #PROJECT_ID}; there is no default for
 * any of them. The automation type is read from the case field named in the constructor.
 */
final class TestRailCaseService implements CaseService {

    static final String URL = "TESTRAIL_URL";
    static final String USER = "TESTRAIL_USER";
    static final String PASSWORD = "TESTRAIL_PASSWORD";
    static final String PROJECT_ID = "TESTRAIL_PROJECT_ID";

    private static final String DISPLAY_NAME = "TestRail";
    private static final List<String> VARIABLES = List.of(URL, USER, PASSWORD, PROJECT_ID);

    private final String automationField;

    /** Reads the automation type from {@code custom_automation_type}. */
    TestRailCaseService() {
        this(CaseRecord.AUTOMATION_TYPE_FIELD);
    }

    /**
     * Reads the automation type from the given case field.
     *
     * @param automationField system name of the case field holding the automation type
     * @throws NullPointerException if {@code automationField} is {@code null}
     * @throws IllegalArgumentException if {@code automationField} is blank or has surrounding whitespace
     */
    TestRailCaseService(String automationField) {
        Objects.requireNonNull(automationField, "automationField");
        if (automationField.isBlank() || !automationField.equals(automationField.strip())) {
            throw new IllegalArgumentException(
                    "The automation field must be a TestRail system name without surrounding whitespace");
        }
        this.automationField = automationField;
    }

    /** What makes two services load different indexes besides their variables: the automation field. */
    private record Settings(String automationField) {}

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public List<String> requiredVariables() {
        return VARIABLES;
    }

    @Override
    public Object shareKey() {
        return new Settings(automationField);
    }

    @Override
    public CaseIndex load(CaseQuery query, Map<String, String> variables) throws CaseServiceUnavailableException {
        TestRailConnection connection = connection(variables);
        try (TestRailClient client = new TestRailClient(connection, automationField)) {
            return new IndexLoader(
                            client,
                            connection.parallelism(),
                            connection.budget(),
                            connection.lookupThreshold(),
                            automationField)
                    .load(query.ids());
        }
    }

    /**
     * Builds the connection from the variables. Messages name the variable at fault and never its value, since a
     * misplaced secret may sit in any of them.
     */
    static TestRailConnection connection(Map<String, String> variables) {
        int projectId = projectId(variables.get(PROJECT_ID));
        String url = variables.get(URL).strip();
        TestRailConnection.urlProblem(url).ifPresent(problem -> {
            throw new UnableToVerifyError(
                    "%s is not a usable TestRail address: it %s, for example https://tms.example.org"
                            .formatted(URL, problem));
        });
        return TestRailConnection.of(url, variables.get(USER), variables.get(PASSWORD), projectId);
    }

    private static int projectId(String value) {
        String text = value.strip();
        if (!text.matches("[0-9]{1,9}") || Integer.parseInt(text) < 1) {
            throw new UnableToVerifyError(
                    "%s must be the positive numeric id of a TestRail project".formatted(PROJECT_ID));
        }
        return Integer.parseInt(text);
    }
}
