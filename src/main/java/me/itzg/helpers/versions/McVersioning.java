package me.itzg.helpers.versions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import me.itzg.helpers.errors.InvalidParameterException;

@UtilityClass
public class McVersioning {

    public static final String SNAPSHOT = "snapshot";
    public static final String LATEST = "latest";
    public static final Pattern MC_VERSION_REGEX = Pattern.compile(
        LATEST + "|" + SNAPSHOT + "|"
            + "[0-9]{2}w[0-9]{2}[a-z]|(?:[0-9]+\\\\.)+[0-9]+(?:-(?:pre|rc)[0-9]+)?|\\\\d+\\\\.\\\\d+|\\\\d+",
        Pattern.CASE_INSENSITIVE
    );

    public static String validateMinecraftVersion(String version) {
        final Matcher m = MC_VERSION_REGEX.matcher(version);
        if (m.matches()) {
            return version;
        }
        throw new InvalidParameterException(String.format("%s is not a valid minecraft version", version));
    }
}
