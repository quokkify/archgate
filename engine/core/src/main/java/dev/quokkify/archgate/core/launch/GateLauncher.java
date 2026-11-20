package dev.quokkify.archgate.core.launch;

import dev.quokkify.archgate.api.ArchitectureRule;
import dev.quokkify.archgate.api.Severity;
import dev.quokkify.archgate.api.UnableToVerifyError;
import dev.quokkify.archgate.core.ContextResolver;
import dev.quokkify.archgate.core.ExternalPolicy;
import dev.quokkify.archgate.core.FailOn;
import dev.quokkify.archgate.core.GateResult;
import dev.quokkify.archgate.core.GateRunServices;
import dev.quokkify.archgate.core.GateRunner;
import dev.quokkify.archgate.core.RegistrationCheck;
import dev.quokkify.archgate.core.ReportFormatter;
import dev.quokkify.archgate.core.RootPackage;
import dev.quokkify.archgate.core.RuleRegistry;
import dev.quokkify.archgate.core.SimpleProjectLayout;
import dev.quokkify.archgate.core.VariableEnvironment;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class GateLauncher {

    private GateLauncher() {}

    public static GateResult run(LaunchRequest request, ReportSink sink) {
        request.reportFile().ifPresent(file -> delete(file, "report file"));
        request.stateFile().ifPresent(file -> delete(file, "state file"));
        String rootPackage = RootPackage.validate(request.rootPackage());
        Optional<Severity> threshold = FailOn.parse(request.failOn());
        ExternalPolicy policy = ExternalPolicy.detect(request.lookup());
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        URLClassLoader loader = new URLClassLoader(urls(request.ruleClasspath()), GateLauncher.class.getClassLoader());
        try {
            thread.setContextClassLoader(loader);
            VariableEnvironment environment = new VariableEnvironment(request.lookup(), policy.offline());
            GateResult result = evaluate(request, rootPackage, loader, policy, environment);
            policy.offlineWarning().ifPresent(warning -> sink.line(Severity.WARNING, warning));
            report(result, threshold, request.reportFile(), sink);
            boolean readEnvironment = environment.wasRead() || result.externallySkipped() > 0;
            request.stateFile().ifPresent(file -> writeState(file, readEnvironment));
            if (result.failsOn(threshold)) {
                throw new GateFailure(
                        "Architecture gate failed: %s; failure policy: failOn=%s"
                                .formatted(
                                        ReportFormatter.counts(result),
                                        threshold.map(Severity::name).orElse("NEVER")),
                        result);
            }
            return result;
        } finally {
            thread.setContextClassLoader(previous);
            close(loader, sink);
        }
    }

    private static GateResult evaluate(
            LaunchRequest request,
            String rootPackage,
            ClassLoader loader,
            ExternalPolicy policy,
            VariableEnvironment environment) {
        List<ArchitectureRule<?>> rules = RuleRegistry.load(loader);
        RegistrationCheck.verify(request.ownDirs(), rules, loader);
        ModuleDirs dirs = request.dirs();
        SimpleProjectLayout layout = new SimpleProjectLayout(
                rootPackage,
                dirs.mainClasses(),
                dirs.testClasses(),
                dirs.mainSources(),
                dirs.testSources(),
                loader,
                new GateRunServices(environment));
        try (ContextResolver contexts = new ContextResolver(layout, loader)) {
            return GateRunner.run(rules, contexts, policy);
        }
    }

    private static void report(
            GateResult result, Optional<Severity> threshold, Optional<Path> reportFile, ReportSink sink) {
        List<String> lines = ReportFormatter.format(result, threshold);
        Severity level = result.worst().orElse(Severity.INFO);
        if (result.externallySkipped() > 0 && level.compareTo(Severity.WARNING) < 0) {
            level = Severity.WARNING;
        }
        Severity logLevel = level;
        lines.forEach(line -> sink.line(logLevel, line));
        reportFile.ifPresent(file -> write(file, lines));
    }

    private static void writeState(Path file, boolean readEnvironment) {
        write(file, "readEnvironment=" + readEnvironment, "state file");
    }

    private static void write(Path file, List<String> lines) {
        write(file, ReportFormatter.fileContent(lines), "report file");
    }

    private static void write(Path file, String content, String what) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (Objects.nonNull(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UnableToVerifyError("Cannot write the " + what + " " + file + ": " + e.getMessage(), e);
        }
    }

    private static void delete(Path file, String what) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UnableToVerifyError("Cannot delete the previous " + what + " " + file + ": " + e.getMessage(), e);
        }
    }

    private static URL[] urls(List<Path> entries) {
        try {
            URL[] urls = new URL[entries.size()];
            for (int i = 0; i < urls.length; i++) {
                urls[i] = entries.get(i).toUri().toURL();
            }
            return urls;
        } catch (MalformedURLException e) {
            throw new UnableToVerifyError("Cannot build the rule classpath: " + e.getMessage(), e);
        }
    }

    private static void close(URLClassLoader loader, ReportSink sink) {
        try {
            loader.close();
        } catch (IOException e) {
            sink.line(Severity.INFO, "Cannot close the test classpath loader: " + e.getMessage());
        }
    }
}
