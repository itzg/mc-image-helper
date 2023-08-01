package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import static org.junit.jupiter.api.Assertions.*;
import static me.itzg.helpers.modrinth.ModrinthTestHelpers.*;

import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.*;

@WireMockTest
public class TestModrinthPackInstaller {
    @Test
    void modpackInstallerReturnsTheModpackIndex(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        SharedFetchArgs fetchArgs = new SharedFetchArgs();
        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            fetchArgs.options());

        ModpackIndex expectedIndex = createBasicModpackIndex();
        Path modpackPath = createModrinthPack(expectedIndex, tempDir);

        Path resultsFile = tempDir.resolve("results");

        ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
            apiClient, fetchArgs, modpackPath, tempDir, resultsFile, false);

        ModpackIndex actualIndex = installerUT.processModpack().block();

        assertNotNull(actualIndex);
        assertEquals(expectedIndex, actualIndex);
    }
}
