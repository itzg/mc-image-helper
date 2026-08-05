package me.itzg.helpers.curseforge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.ObbyLoader;
import org.slf4j.Logger;

@Slf4j
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
        if (apiKey == null) {
            log.error("No API key was provided. Please set the environment variable "
                + CurseForgeApiClient.API_KEY_VAR + ".");
        }
        else if (apiKey.startsWith("$$")) {
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

    /**
     * @throws InvalidParameterException if no API key is provided or cannot be loaded
     */
    public static String loadApiKey(String providedApiKey) {
        return loadApiKey(providedApiKey, null);
    }

    public static String loadApiKey(String providedApiKey, Path apiKeyFile) {
        if (providedApiKey != null && !providedApiKey.isBlank()) {
            log.debug("Using provided CurseForge API key");
            return providedApiKey.trim();
        }

        if (apiKeyFile != null) {
            final String loadedApiKey;
            try {
                loadedApiKey = Files.readString(apiKeyFile, StandardCharsets.UTF_8).trim();
            } catch (IOException | RuntimeException e) {
                throw new InvalidParameterException(
                    "Unable to read CurseForge API key file " + apiKeyFile, e, true
                );
            }
            if (loadedApiKey.isBlank()) {
                throw new InvalidParameterException(
                    "CurseForge API key file " + apiKeyFile + " is empty"
                );
            }
            log.debug("Loaded CurseForge API key from file");
            return loadedApiKey;
        }

        // properties file and property need to match the ObbyTask in build.gradle
        final Map<String, String> cfApiProperties = ObbyLoader.loadProperties("/cf-api.properties");
        final String loadedApiKey = cfApiProperties.get("cfApiKey");

        if (loadedApiKey == null || loadedApiKey.isBlank()) {
            throw new InvalidParameterException("CurseForge API key is required");
        }
        log.debug("Loaded CurseForge API key from cf-api.properties");
        return loadedApiKey.trim();
    }

}
