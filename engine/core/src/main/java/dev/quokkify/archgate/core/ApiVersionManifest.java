package dev.quokkify.archgate.core;

import dev.quokkify.archgate.api.UnableToVerifyError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Objects;
import java.util.Optional;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

final class ApiVersionManifest {

    static final String ATTRIBUTE = "Archgate-Api-Version";

    private static final String FILE_SCHEME = "file";

    private ApiVersionManifest() {}

    static Optional<String> of(Class<?> type) {
        return jar(type).flatMap(ApiVersionManifest::declared);
    }

    static Optional<Path> jar(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (Objects.isNull(source) || Objects.isNull(source.getLocation())) {
            return Optional.empty();
        }
        try {
            URI uri = source.getLocation().toURI();
            if (!FILE_SCHEME.equalsIgnoreCase(uri.getScheme())) {
                return Optional.empty();
            }
            Path location = Path.of(uri);
            return Files.isRegularFile(location) ? Optional.of(location) : Optional.empty();
        } catch (URISyntaxException | IllegalArgumentException invalidLocation) {
            return Optional.empty();
        }
    }

    static Optional<String> declared(Path jar) {
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            return Objects.isNull(manifest)
                    ? Optional.empty()
                    : Optional.ofNullable(manifest.getMainAttributes().getValue(ATTRIBUTE));
        } catch (IOException e) {
            throw new UnableToVerifyError("Cannot read the manifest of " + jar + ": " + e.getMessage(), e);
        }
    }
}
