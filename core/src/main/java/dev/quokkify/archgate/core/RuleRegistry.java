package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.UnableToVerifyError;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleRegistry {

    public static final String SERVICES_FILE = "META-INF/services/dev.quokkify.archgate.api.ArchitectureRule";

    private static final Pattern LIBRARY_RULES_CLASS = Pattern.compile("dev\\.quokkify\\.archgate\\.rules\\.(\\w+)\\.");

    private RuleRegistry() {}

    public static List<ArchitectureRule<?>> load(ClassLoader loader) {
        return load(loader, runningApi());
    }

    static List<ArchitectureRule<?>> load(ClassLoader loader, Optional<ApiVersion> runtime) {
        try {
            Map<Class<? extends ArchitectureRule>, ServiceLoader.Provider<ArchitectureRule>> providers =
                    new LinkedHashMap<>();
            ServiceLoader.load(ArchitectureRule.class, loader).stream()
                    .forEach(provider -> providers.putIfAbsent(provider.type(), provider));
            ApiCompatibilityCheck.verify(runtime, providers.keySet());
            List<ArchitectureRule<?>> rules = providers.values().stream()
                    .<ArchitectureRule<?>>map(ServiceLoader.Provider::get)
                    .sorted(Comparator.comparing(ArchitectureRule::name))
                    .toList();
            if (rules.isEmpty()) {
                throw new UnableToVerifyError("no rules registered: " + SERVICES_FILE
                        + " is missing or lists no rule classes on the test classpath.");
            }
            return rules;
        } catch (ServiceConfigurationError e) {
            throw new UnableToVerifyError(describe(e), e);
        }
    }

    private static Optional<ApiVersion> runningApi() {
        try {
            return ApiVersionManifest.of(ArchitectureRule.class).map(ApiVersion::parse);
        } catch (IllegalArgumentException e) {
            throw new UnableToVerifyError(
                    "The running archgate-api declares an invalid " + ApiVersionManifest.ATTRIBUTE + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private static String describe(ServiceConfigurationError error) {
        String message = "Cannot load rules listed in " + SERVICES_FILE + ": " + error.getMessage();
        Throwable cause = error.getCause();
        if (Objects.nonNull(cause) && Objects.nonNull(cause.getMessage())) {
            message += " (" + cause + ")";
        }
        Matcher library = LIBRARY_RULES_CLASS.matcher(message);
        if (library.find()) {
            message += " Add dev.quokkify.archgate:archgate-rules-" + library.group(1) + " as a test dependency.";
        }
        return message;
    }
}
