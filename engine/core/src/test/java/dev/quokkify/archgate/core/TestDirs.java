package dev.quokkify.archgate.core;

import archfixtures.registry.ValidRule;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

final class TestDirs {

    private TestDirs() {}

    static Path classes() {
        return locationOf(ValidRule.class.getProtectionDomain().getCodeSource().getLocation());
    }

    static Path resources() {
        return locationOf(TestDirs.class.getResource("/registry")).getParent();
    }

    private static Path locationOf(URL url) {
        try {
            return Paths.get(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
