package me.itzg.helpers.versions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import me.itzg.helpers.errors.InvalidParameterException;
import org.jspecify.annotations.Nullable;

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
        throw new InvalidParameterException(String.format("%s is not a valid minecraft version", version));
    }

    public static boolean isSnapshot(@Nullable String version) {
        return version != null && version.equalsIgnoreCase(SNAPSHOT);
    }

    public static boolean isLatest(@Nullable String version) {
        return version == null || version.equalsIgnoreCase(LATEST);
    }
}
