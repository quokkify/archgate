package dev.quokkify.archgate.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkParameters;

public interface GateWorkParameters extends WorkParameters {

    Property<String> getRootPackage();

    Property<String> getFailOn();

    ConfigurableFileCollection getMainClassesDirs();

    ConfigurableFileCollection getTestClassesDirs();

    ConfigurableFileCollection getMainSourceDirs();

    ConfigurableFileCollection getTestSourceDirs();

    ConfigurableFileCollection getOwnDirs();

    ConfigurableFileCollection getRuleClasspath();

    RegularFileProperty getReportFile();
}
