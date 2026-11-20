package dev.quokkify.archgate.rules.archunit.testplanit;

import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.cases.archunit.CaseIndex;
import dev.quokkify.archgate.cases.archunit.CaseQuery;
import dev.quokkify.archgate.cases.archunit.CaseService;
import dev.quokkify.archgate.cases.archunit.CaseServiceUnavailableException;
import java.util.List;
import java.util.Map;

/**
 * Looks up linked cases in one TestPlanIt project through its model API. The server, the API token and the project
 * come from the variables {@value #URL}, {@value #TOKEN} and {@value #PROJECT_ID}; there is no default for any of
 * them.
 */
final class TestPlanItCaseService implements CaseService {

    static final String URL = "TESTPLANIT_URL";
    static final String TOKEN = "TESTPLANIT_TOKEN";
    static final String PROJECT_ID = "TESTPLANIT_PROJECT_ID";

    private static final String DISPLAY_NAME = "TestPlanIt";
    private static final List<String> VARIABLES = List.of(URL, TOKEN, PROJECT_ID);

    @Override
    public String displayName() {
        return DISPLAY_NAME;
    }

    @Override
    public List<String> requiredVariables() {
        return VARIABLES;
    }

    @Override
    public CaseIndex load(CaseQuery query, Map<String, String> variables) throws CaseServiceUnavailableException {
        TestPlanItConnection connection = connection(variables);
        try (TestPlanItClient client = new TestPlanItClient(connection)) {
            return new CaseLookup(client, connection).load(query.ids());
        }
    }

    /**
     * Builds the connection from the variables. Messages name the variable at fault and never its value, since a
     * misplaced secret may sit in any of them.
     */
    static TestPlanItConnection connection(Map<String, String> variables) {
        int projectId = projectId(variables.get(PROJECT_ID));
        String token = variables.get(TOKEN).strip();
        if (!token.matches("[\\x21-\\x7E]+")) {
            throw new UnableToVerifyError(
                    "%s must be a TestPlanIt API token without spaces or control characters".formatted(TOKEN));
        }
        String url = variables.get(URL).strip();
        TestPlanItConnection.urlProblem(url).ifPresent(problem -> {
            throw new UnableToVerifyError(
                    "%s is not a usable TestPlanIt address: it %s, for example https://tms.example.org"
                            .formatted(URL, problem));
        });
        return TestPlanItConnection.of(url, token, projectId);
    }

    private static int projectId(String value) {
        String text = value.strip();
        if (!text.matches("[0-9]{1,9}") || Integer.parseInt(text) < 1) {
            throw new UnableToVerifyError(
                    "%s must be the positive numeric id of a TestPlanIt project".formatted(PROJECT_ID));
        }
        return Integer.parseInt(text);
    }
}
