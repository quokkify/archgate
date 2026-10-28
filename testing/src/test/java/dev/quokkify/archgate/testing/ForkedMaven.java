package dev.quokkify.archgate.testing;

import org.apache.maven.shared.verifier.Verifier;

public final class ForkedMaven {

    private static final String JAVA_HOME = "JAVA_HOME";

    private ForkedMaven() {}

    public static Verifier pinJavaHome(Verifier verifier) {
        verifier.setEnvironmentVariable(JAVA_HOME, System.getProperty("java.home"));
        return verifier;
    }
}
