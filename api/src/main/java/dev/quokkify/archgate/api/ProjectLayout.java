package dev.quokkify.archgate.api;

import java.nio.file.Path;
import java.util.List;

public interface ProjectLayout {

    String rootPackage();

    List<Path> classDirs(Scope scope);

    List<Path> sourceDirs(Scope scope);

    ClassLoader classLoader();
}
