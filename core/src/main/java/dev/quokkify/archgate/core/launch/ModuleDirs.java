package dev.quokkify.archgate.core.launch;

import java.nio.file.Path;
import java.util.List;

public record ModuleDirs(
        List<Path> mainClasses, List<Path> testClasses, List<Path> mainSources, List<Path> testSources) {}
