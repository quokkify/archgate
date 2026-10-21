package dev.quokkify.archgate.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Builds real rule jars and class directories from compiled fixtures under {@code archfixtures.version} and loads them
 * through a parent that hides that package, so the classes come from the jar or directory and not from
 * the test classes directory. Fixtures in other packages (for example {@code archfixtures.registry}) stay visible
 * from the test classes directory, which the "rule from a directory" cases rely on.
 */
final class RuleJars implements Closeable {

    private static final String HIDDEN_PACKAGE = "archfixtures.version.";

    private final List<URLClassLoader> loaders = new ArrayList<>();

    static Path write(Path file, String apiVersion, List<Class<?>> classes) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (Objects.nonNull(apiVersion)) {
            manifest.getMainAttributes().putValue(ApiVersionManifest.ATTRIBUTE, apiVersion);
        }
        try (OutputStream out = Files.newOutputStream(file);
                JarOutputStream jar = new JarOutputStream(out, manifest)) {
            writeEntries(jar, classes);
        }
        return file;
    }

    static Path writeWithoutManifest(Path file, List<Class<?>> classes) throws IOException {
        try (OutputStream out = Files.newOutputStream(file);
                JarOutputStream jar = new JarOutputStream(out)) {
            writeEntries(jar, classes);
        }
        return file;
    }

    static Path directory(Path dir, List<Class<?>> classes) throws IOException {
        for (Class<?> type : classes) {
            Path target = dir.resolve(entryName(type));
            Files.createDirectories(target.getParent());
            Files.write(target, bytes(type));
        }
        return dir;
    }

    URLClassLoader loader(Path... entries) throws IOException {
        URL[] urls = new URL[entries.length];
        for (int i = 0; i < entries.length; i++) {
            urls[i] = entries[i].toUri().toURL();
        }
        URLClassLoader loader = new URLClassLoader(urls, new HidingParent(RuleJars.class.getClassLoader()));
        loaders.add(loader);
        return loader;
    }

    static Class<?> defineAt(Class<?> fixture, URL location) throws IOException {
        byte[] bytes = bytes(fixture);
        ProtectionDomain domain = new ProtectionDomain(new CodeSource(location, (Certificate[]) null), null);
        return new HidingParent(RuleJars.class.getClassLoader()).define(fixture.getName(), bytes, domain);
    }

    @Override
    public void close() throws IOException {
        for (URLClassLoader loader : loaders) {
            loader.close();
        }
    }

    private static void writeEntries(JarOutputStream jar, List<Class<?>> classes) throws IOException {
        for (Class<?> type : classes) {
            jar.putNextEntry(new JarEntry(entryName(type)));
            jar.write(bytes(type));
            jar.closeEntry();
        }
        String services = classes.stream().map(Class::getName).reduce("", (text, name) -> text + name + "\n");
        jar.putNextEntry(new JarEntry(RuleRegistry.SERVICES_FILE));
        jar.write(services.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }

    private static String entryName(Class<?> type) {
        return type.getName().replace('.', '/') + ".class";
    }

    private static byte[] bytes(Class<?> type) throws IOException {
        try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return Objects.requireNonNull(in, type.getName()).readAllBytes();
        }
    }

    private static final class HidingParent extends ClassLoader {

        HidingParent(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes, ProtectionDomain domain) {
            return defineClass(name, bytes, 0, bytes.length, domain);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(HIDDEN_PACKAGE)) {
                Class<?> defined = findLoadedClass(name);
                if (Objects.nonNull(defined)) {
                    return defined;
                }
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        public URL getResource(String name) {
            return RuleRegistry.SERVICES_FILE.equals(name) ? null : super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return RuleRegistry.SERVICES_FILE.equals(name) ? Collections.emptyEnumeration() : super.getResources(name);
        }
    }
}
