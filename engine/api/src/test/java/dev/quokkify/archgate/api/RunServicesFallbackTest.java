package dev.quokkify.archgate.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.testng.annotations.Test;

class RunServicesFallbackTest {

    @Test
    void contextWithoutServicesFailsActionably() {
        AnalysisContext context = new AnalysisContext() {};

        assertThatThrownBy(context::environment)
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("ProjectLayout.services()");
    }

    @Test
    void contextWithoutSharedValuesFailsActionably() {
        AnalysisContext context = new AnalysisContext() {};

        assertThatThrownBy(() -> context.shared("load", () -> "v"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("provides no shared values")
                .hasMessageContaining("ProjectLayout.services()");
    }

    @Test
    void servicesWithoutSharedValuesFailActionably() {
        RunServices services = () -> null;

        assertThatThrownBy(() -> services.shared("load", () -> "v"))
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("cannot share values")
                .hasMessageContaining("RunServices.shared");
    }

    @Test
    void layoutWithoutServicesFailsActionably() {
        ProjectLayout layout = new ProjectLayout() {
            @Override
            public String rootPackage() {
                return "p";
            }

            @Override
            public List<Path> classDirs(Scope scope) {
                return List.of();
            }

            @Override
            public List<Path> sourceDirs(Scope scope) {
                return List.of();
            }

            @Override
            public ClassLoader classLoader() {
                return getClass().getClassLoader();
            }
        };

        assertThatThrownBy(layout::services)
                .isInstanceOf(UnableToVerifyError.class)
                .hasMessageContaining("ProjectLayout.services()");
    }
}
