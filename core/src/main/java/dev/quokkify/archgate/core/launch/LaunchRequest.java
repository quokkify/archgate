package dev.quokkify.archgate.core.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record LaunchRequest(
        String rootPackage,
        String failOn,
        ModuleDirs dirs,
        List<Path> ownDirs,
        List<Path> ruleClasspath,
        Optional<Path> reportFile) {}
