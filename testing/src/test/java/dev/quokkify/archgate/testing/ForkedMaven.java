package dev.quokkify.archgate.testing;

import org.apache.maven.executor.ExecutorRequest;

public final class ForkedMaven {

    private static final String JAVA_HOME = "JAVA_HOME";

    private ForkedMaven() {}

    public static ExecutorRequest.Builder pinJavaHome(ExecutorRequest.Builder request) {
        return request.environmentVariable(JAVA_HOME, System.getProperty("java.home"));
    }
}
