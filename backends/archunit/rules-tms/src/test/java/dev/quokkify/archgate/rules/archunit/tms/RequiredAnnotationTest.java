package dev.quokkify.archgate.rules.archunit.tms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.testng.annotations.Test;

public class RequiredAnnotationTest {

    @Test(description = "of keeps the name, derives the simple name and has no options")
    public void ofKeepsNameAndSimpleName() {
        RequiredAnnotation annotation = RequiredAnnotation.of("com.acme.Smoke");

        assertThat(annotation.name()).isEqualTo("com.acme.Smoke");
        assertThat(annotation.simpleName()).isEqualTo("Smoke");
        assertThat(annotation.container()).isEmpty();
        assertThat(annotation.nonBlankValue()).isFalse();
    }

    @Test(description = "simple name of a nested annotation is the part after the last dollar sign")
    public void nestedAnnotationSimpleName() {
        assertThat(RequiredAnnotation.of("com.acme.Outer$Inner").simpleName()).isEqualTo("Inner");
    }

    @Test(description = "options return a new instance and leave the original unchanged")
    public void optionsReturnNewInstance() {
        RequiredAnnotation base = RequiredAnnotation.of("com.acme.Smoke");

        RequiredAnnotation configured = base.withContainer("com.acme.Groups").requiringNonBlankValue();

        assertThat(configured.container()).isEqualTo(Optional.of("com.acme.Groups"));
        assertThat(configured.nonBlankValue()).isTrue();
        assertThat(base.container()).isEmpty();
        assertThat(base.nonBlankValue()).isFalse();
    }

    @Test(description = "null names are rejected with NullPointerException")
    public void rejectsNull() {
        assertThatThrownBy(() -> RequiredAnnotation.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RequiredAnnotation.of("com.acme.A").withContainer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test(description = "blank or unqualified names are rejected and named in the message")
    public void rejectsBlankOrUnqualified() {
        assertThatThrownBy(() -> RequiredAnnotation.of(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("' '");
        assertThatThrownBy(() -> RequiredAnnotation.of("Smoke"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Smoke");
        assertThatThrownBy(() -> RequiredAnnotation.of("com.acme.A").withContainer("Groups"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Groups");
    }

    @Test(description = "names with a leading or trailing dot or dollar sign, or surrounding whitespace, are rejected")
    public void rejectsMalformedNames() {
        String[] malformedNames = {"com.", ".Foo", "com.acme.", "com.acme.Foo$", " com.acme.Foo", "com.acme.Foo "};
        for (String malformed : malformedNames) {
            assertThatThrownBy(() -> RequiredAnnotation.of(malformed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(malformed);
            assertThatThrownBy(() -> RequiredAnnotation.of("com.acme.A").withContainer(malformed))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(malformed);
        }
    }
}
