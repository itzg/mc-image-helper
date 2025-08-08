package me.itzg.helpers.curseforge;

import org.slf4j.Logger;

public class ApiKeyHelper {

    public static final String EXPECTED_API_KEY_PREFIX = "$2a$10$";

    static String partiallyRedactApiKey(String apiKey) {
        final int trailingAmount = 2;
        // too short?
        if (apiKey.length() <= trailingAmount + EXPECTED_API_KEY_PREFIX.length()) {
            // show half of it
            return apiKey.substring(0, apiKey.length() / 2);
        }

        return apiKey.substring(0, EXPECTED_API_KEY_PREFIX.length()) +
            "*****" + apiKey.substring(apiKey.length() - trailingAmount);
    }

    public static void logKeyIssues(Logger log, String apiKey) {
        if (apiKey.startsWith("$$")) {
            log.error("The API key seems to have extra dollar sign escaping since "
                    + "it looked like '{}' but should start with '{}'.",
                partiallyRedactApiKey(apiKey), EXPECTED_API_KEY_PREFIX
            );
        }
        else if (!apiKey.startsWith(EXPECTED_API_KEY_PREFIX)) {
            log.error("The API key should start with '{}' but yours looked like '{}'."
                    + " Make sure to escape dollar signs with two each.",
                EXPECTED_API_KEY_PREFIX, partiallyRedactApiKey(apiKey)
            );
        }
    }
}
