package me.itzg.helpers.curseforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiKeyHelperTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsApiKeyFromFileEnvironmentVariable() throws Exception {
        final Path apiKeyFile = Files.writeString(tempDir.resolve("cf-api-key"), "key-from-file\n");

        assertThat(ApiKeyHelper.loadApiKey(null, Map.of(CurseForgeApiClient.API_KEY_FILE_VAR, apiKeyFile.toString())))
            .isEqualTo("key-from-file");
    }

    @Test
    void providedApiKeyTakesPrecedenceOverFileEnvironmentVariable() throws Exception {
        final Path apiKeyFile = Files.writeString(tempDir.resolve("cf-api-key"), "key-from-file\n");

        assertThat(ApiKeyHelper.loadApiKey("provided", Map.of(CurseForgeApiClient.API_KEY_FILE_VAR, apiKeyFile.toString())))
            .isEqualTo("provided");
    }

    @Test
    void unreadableApiKeyFileIsAConfigurationError() {
        final Path missingFile = tempDir.resolve("missing-cf-api-key");

        assertThatThrownBy(() -> ApiKeyHelper.loadApiKey(
            null, Map.of(CurseForgeApiClient.API_KEY_FILE_VAR, missingFile.toString())))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining(CurseForgeApiClient.API_KEY_FILE_VAR);
    }
}
