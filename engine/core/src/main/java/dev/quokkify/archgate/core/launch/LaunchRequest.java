package dev.quokkify.archgate.core.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public record LaunchRequest(
        String rootPackage,
        String failOn,
        String reportStyle,
        ModuleDirs dirs,
        List<Path> ownDirs,
        List<Path> ruleClasspath,
        Optional<Path> reportFile,
        Function<String, String> lookup,
        Optional<Path> stateFile) {}
