/**
 * Rules that check {@code @TmsLink} case ids against a TestRail project.
 *
 * <p>{@link dev.quokkify.archgate.rules.archunit.testrail.TestRailCaseExistsRule} reports ids without a live case
 * in the project, {@link dev.quokkify.archgate.rules.archunit.testrail.TestRailCaseAutomatedRule} reports linked
 * cases whose automation type is not an automated one. TestRail is read through its REST API with the JDK HTTP
 * client, once per run for both rules.
 */
package dev.quokkify.archgate.rules.archunit.testrail;
