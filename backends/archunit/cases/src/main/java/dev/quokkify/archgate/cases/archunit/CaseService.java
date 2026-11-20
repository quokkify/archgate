package dev.quokkify.archgate.cases.archunit;

import java.util.List;
import java.util.Map;

/**
 * Looks up test cases in one test-management system.
 *
 * <p>The rules call {@link #load} at most once per run and mode, and only when some test links a case. The
 * implementation must be thread-safe and must not log.
 */
public interface CaseService {

    /**
     * Names the system in rule names, finding titles, the shared index name and skip messages, for example
     * {@code TestRail}.
     *
     * @return the display name, not blank
     */
    String displayName();

    /**
     * Lists the variables the service cannot run without, such as a URL and credentials. They are required
     * together, and only when some test links a case, so a project without links needs none of them.
     *
     * @return the variable names, possibly empty
     */
    List<String> requiredVariables();

    /**
     * Looks up the queried cases.
     *
     * @param query the linked case ids and the mode of the run
     * @param variables the value of every name in {@link #requiredVariables()}
     * @return an index that places every queried id in exactly one of its categories
     * @throws CaseServiceUnavailableException if the system does not answer, answers with an error or answers with
     *     something unreadable; the rule is then skipped outside CI. Refused credentials or settings that cannot work
     *     are no outage: throw {@link dev.quokkify.archgate.api.UnableToVerifyError} with an actionable message
     *     instead, which fails the run wherever it runs
     */
    CaseIndex load(CaseQuery query, Map<String, String> variables) throws CaseServiceUnavailableException;

    /**
     * Identifies the configuration of this service, so rules of one run share a load only when their services
     * would load the same index.
     *
     * <p>Rules share the index of a run by display name and mode; they reuse a loaded index only when the share
     * key, the service class, the variable values and the query are all equal, and otherwise fail the run as
     * unable to verify. Override this when the service takes settings of its own, for example through a
     * subclass constructor, and return a value with {@code equals} and {@code hashCode} (a record works well)
     * that holds every such setting affecting {@link #load}. The variables need not be included; they are
     * compared separately. The value is never printed.
     *
     * @return the share key, not {@code null}; by default the service class, which suits a service without
     *     settings of its own
     */
    default Object shareKey() {
        return getClass();
    }
}
