package dev.quokkify.archgate.core;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ApiVersion implements Comparable<ApiVersion> {

    static final String DEV = "dev-SNAPSHOT";
    static final String EXPECTED_FORMAT = "X.Y.Z or " + DEV;

    // three dot-separated non-negative integers, nothing else
    private static final Pattern RELEASE = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Comparator<ApiVersion> ORDER = Comparator.comparing((ApiVersion version) -> version.dev)
            .thenComparingInt(version -> version.major)
            .thenComparingInt(version -> version.minor)
            .thenComparingInt(version -> version.patch);

    private final String text;
    private final boolean dev;
    private final int major;
    private final int minor;
    private final int patch;

    private ApiVersion(String text, boolean dev, int major, int minor, int patch) {
        this.text = text;
        this.dev = dev;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    static ApiVersion parse(String text) {
        if (DEV.equals(text)) {
            return new ApiVersion(text, true, 0, 0, 0);
        }
        Matcher release = RELEASE.matcher(text);
        if (!release.matches()) {
            throw new IllegalArgumentException("'" + text + "' is not a version, expected " + EXPECTED_FORMAT);
        }
        return new ApiVersion(text, false, number(release, 1), number(release, 2), number(release, 3));
    }

    private static int number(Matcher release, int group) {
        try {
            return Integer.parseInt(release.group(group));
        } catch (NumberFormatException tooLarge) {
            throw new IllegalArgumentException(
                    "'" + release.group() + "' is not a version, expected " + EXPECTED_FORMAT, tooLarge);
        }
    }

    boolean isDev() {
        return dev;
    }

    @Override
    public int compareTo(ApiVersion other) {
        return ORDER.compare(this, other);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ApiVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dev, major, minor, patch);
    }

    @Override
    public String toString() {
        return text;
    }
}
