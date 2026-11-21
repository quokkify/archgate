package archfixtures.disabled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Disabled;

/** Fixture: a composed annotation that disables a JUnit 5 test through {@code @Disabled}. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Disabled("quarantined")
public @interface Quarantined {}
