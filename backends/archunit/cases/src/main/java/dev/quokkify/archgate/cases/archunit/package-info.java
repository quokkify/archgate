/**
 * Backend-neutral checks of {@code @TmsLink} case ids against a test-management system.
 *
 * <p>A backend module implements {@link dev.quokkify.archgate.cases.archunit.CaseService} for one system and
 * ships concrete subclasses of {@link dev.quokkify.archgate.cases.archunit.CaseExistsRule} and
 * {@link dev.quokkify.archgate.cases.archunit.CaseAutomatedRule}. Both rules of one run share a single load
 * of the linked cases.
 */
package dev.quokkify.archgate.cases.archunit;
