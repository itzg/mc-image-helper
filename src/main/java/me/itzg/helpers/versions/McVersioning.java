package me.itzg.helpers.versions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import me.itzg.helpers.errors.InvalidParameterException;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;
import picocli.CommandLine.ParameterException;

@UtilityClass
public class McVersioning {

    public static final String SNAPSHOT = "snapshot";
    public static final String LATEST = "latest";
    public static final Pattern MC_VERSION_REGEX = Pattern.compile(
        LATEST + "|" + SNAPSHOT + "|"
            + "(?:\\d+\\.)+\\d+(?:-(?:snapshot|rc|pre)-?\\d+| Pre-Release \\d+)?"
            + "|[0-9]{2}w[0-9]{2}[a-z_]+"
            + "|[abc](?:\\d+\\.)+\\d+[abc]?(?:_\\d+[abc]?)?"
            + "|(?:inf|rd)-\\d+"
            + "|3D Shareware v1.34",
        Pattern.CASE_INSENSITIVE
    );

    public static String validateMinecraftVersion(@Nullable String version) {
        if (version == null) {
            return null;
        }
        if (version.isBlank()) {
            return null;
        }
        final Matcher m = MC_VERSION_REGEX.matcher(version);
        if (m.matches()) {
            return version;
        }
        throw new InvalidParameterException("Invalid value for minecraft version: " + version);
    }

    public static String validateMinecraftVersion(String version, CommandLine commandLine) {
        try {
            return validateMinecraftVersion(version);
        } catch (InvalidParameterException e) {
            throw new ParameterException(commandLine, "Invalid value for minecraft version: " + version);
        }
    }

    public static boolean isSnapshot(@Nullable String version) {
        return version != null && version.equalsIgnoreCase(SNAPSHOT);
    }

    public static boolean isLatest(@Nullable String version) {
        return version == null || version.equalsIgnoreCase(LATEST);
    }

    public static int compare(String leftVersion, String rightVersion) {
        if (leftVersion == null || leftVersion.isEmpty()) {
            throw new InvalidParameterException("Left version is required");
        }

        if (rightVersion == null || rightVersion.isEmpty()) {
            throw new InvalidParameterException("Right version is required");
        }

        char leftVersionChannel = leftVersion.charAt(0);
        leftVersionChannel = Character.isDigit(leftVersionChannel) ? 'r' : leftVersionChannel;
        char rightVersionChannel = rightVersion.charAt(0);
        rightVersionChannel = Character.isDigit(rightVersionChannel) ? 'r' : rightVersionChannel;

        if (leftVersionChannel != rightVersionChannel) {
            return Character.compare(leftVersionChannel, rightVersionChannel);
        }

        if (leftVersion.startsWith("a") || leftVersion.startsWith("b")) {
            leftVersion = leftVersion.substring(1);
        }
        if (rightVersion.startsWith("a") || rightVersion.startsWith("b")) {
            rightVersion = rightVersion.substring(1);
        }

        return new ComparableVersion(leftVersion).compareTo(new ComparableVersion(rightVersion));
    }
}
