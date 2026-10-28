package dev.quokkify.archgate.gradle;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

final class ExistingDirectories {

    private ExistingDirectories() {}

    static List<Path> of(Collection<File> files) {
        return files.stream().map(File::toPath).filter(Files::isDirectory).toList();
    }
}
