package me.itzg.helpers.users;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles quirky stuff with UUIDs in Mojang's API response, etc
 */
public class UuidQuirks {

    static final Pattern ID_OR_UUID = Pattern.compile("(?<nonDashed>[a-f0-9]{32})"
        + "|(?<dashed>[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");

    public static String addDashesToId(String nonDashed) {
        if (nonDashed.length() != 32) {
            throw new IllegalArgumentException("Input needs to be 32 characters: " + nonDashed);
        }

        return String.join("-",
            nonDashed.substring(0, 8),
            nonDashed.substring(8, 12),
            nonDashed.substring(12, 16),
            nonDashed.substring(16, 20),
            nonDashed.substring(20, 32)
        );
    }

    public static boolean isIdOrUuid(String input) {
        return ID_OR_UUID.matcher(input).matches();
    }

    /**
     *
     * @param input ID (no dashes), UUID, or something else, like a username
     * @return if input is a valid ID/UUID, populated and normalized UUID string
     */
    public static Optional<String> ifIdOrUuid(String input) {
        final Matcher uuidMatcher = UuidQuirks.ID_OR_UUID.matcher(input);
        if (uuidMatcher.matches()) {
            final String uuid = UuidQuirks.normalizeToUuid(uuidMatcher);

            return Optional.of(uuid);
        }
        else {
            return Optional.empty();
        }
    }

    static String normalizeToUuid(Matcher uuidMatcher) {
        final String dashed = uuidMatcher.group("dashed");

        return dashed != null ? dashed :
            UuidQuirks.addDashesToId(uuidMatcher.group("nonDashed"));
    }
}
