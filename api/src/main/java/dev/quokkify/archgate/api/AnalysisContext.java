package dev.quokkify.archgate.api;

import java.time.Duration;
import java.util.Map;

public interface AnalysisContext {

    default Map<String, Duration> buildTimes() {
        return Map.of();
    }
}
