package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.ProjectLayout;
import dev.quokkify.archgate.api.Scope;
import java.nio.file.Path;
import java.util.List;

public record SimpleProjectLayout(
        String rootPackage,
        List<Path> mainClassDirs,
        List<Path> testClassDirs,
        List<Path> mainSourceDirs,
        List<Path> testSourceDirs,
        ClassLoader classLoader)
        implements ProjectLayout {

    @Override
    public List<Path> classDirs(Scope scope) {
        return switch (scope) {
            case MAIN -> mainClassDirs;
            case TEST -> testClassDirs;
        };
    }

    @Override
    public List<Path> sourceDirs(Scope scope) {
        return switch (scope) {
            case MAIN -> mainSourceDirs;
            case TEST -> testSourceDirs;
        };
    }
}
