package dev.quokkify.archgate.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
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

    RegularFileProperty getStateFile();

    /**
     * Returns the CI switches set as Gradle properties, keyed by property name.
     *
     * @return the switches; only {@code archgate.ci}, {@code archgate.offline} and
     *     {@code archgate.ci.serviceUnavailable} are ever present
     */
    MapProperty<String, String> getSwitches();
}
