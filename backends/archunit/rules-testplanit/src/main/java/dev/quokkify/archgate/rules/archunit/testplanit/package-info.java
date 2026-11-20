/**
 * Rules that check {@code @TmsLink} case ids against a TestPlanIt project.
 *
 * <p>{@link dev.quokkify.archgate.rules.archunit.testplanit.TestPlanItCaseExistsRule} reports ids without a live
 * case in the project, {@link dev.quokkify.archgate.rules.archunit.testplanit.TestPlanItCaseAutomatedRule} reports
 * linked cases that are not marked automated. TestPlanIt is read through its model API with the JDK HTTP client,
 * once per run for both rules.
 */
package dev.quokkify.archgate.rules.archunit.testplanit;
