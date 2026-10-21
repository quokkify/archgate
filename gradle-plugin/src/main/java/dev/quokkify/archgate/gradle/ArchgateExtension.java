package dev.quokkify.archgate.gradle;

import java.util.List;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class ArchgateExtension {

    public abstract Property<String> getRootPackage();

    public abstract Property<String> getFailOn();

    public abstract Property<Boolean> getSkip();

    public abstract ListProperty<String> getModules();

    public void modules(String... projectPaths) {
        getModules().addAll(List.of(projectPaths));
    }
}
