package dev.quokkify.archgate.cases.archunit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.List;
import org.testng.annotations.Test;

public class LinkedCasesTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages("archfixtures.linkedcases");

    @Test(description = "Ids are stripped, lose the C prefix and drop non-numeric, zero, empty and too large values")
    public void normalizesIds() {
        LinkedCases cases = LinkedCases.from(FIXTURES);

        assertThat(cases.ids()).containsExactly(12, 13, 14);
        assertThat(cases.isEmpty()).isFalse();
        List<String> names =
                cases.byId().get(12).stream().map(JavaMethod::getName).toList();
        assertThat(names).containsExactly("lowerCasePrefixed", "plain", "upperCasePrefixed");
    }

    @Test(description = "TmsLink on data providers and configuration methods is ignored")
    public void ignoresNonTestMethods() {
        LinkedCases cases = LinkedCases.from(FIXTURES);

        assertThat(cases.ids()).doesNotContain(98, 99);
    }

    @Test(description = "Callers cannot change the discovered links")
    public void linksAreUnmodifiable() {
        LinkedCases cases = LinkedCases.from(FIXTURES);

        assertThatThrownBy(() -> cases.ids().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cases.byId().remove(12)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> cases.byId().get(12).clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(cases.ids()).containsExactly(12, 13, 14);
    }

    @Test(description = "No classes yield an empty result in a full scan")
    public void emptyWhenNothingLinked() {
        LinkedCases cases = LinkedCases.from(new ClassFileImporter().importClasses(String.class));

        assertThat(cases.isEmpty()).isTrue();
        assertThat(cases.changedFilesOnly()).isFalse();
    }
}
