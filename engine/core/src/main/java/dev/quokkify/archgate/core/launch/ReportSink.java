package dev.quokkify.archgate.core.launch;

import dev.quokkify.archgate.api.Severity;

@FunctionalInterface
public interface ReportSink {

    void line(Severity level, String text);
}
