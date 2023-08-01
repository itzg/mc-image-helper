package me.itzg.helpers.modrinth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import static org.junit.jupiter.api.Assertions.*;

import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.*;

@WireMockTest
public class TestModrinthPackInstaller {
    @Test
    void modpackInstallerReturnsTheModpackIndex(
            WireMockRuntimeInfo wm, @TempDir Path tempDir
        ) throws IOException
    {
        ObjectMapper mapper = new ObjectMapper();

        ModpackIndex expectedIndex = new ModpackIndex()
            .setName(null)
            .setGame("minecraft")
            .setDependencies(new HashMap<DependencyId, String>())
            .setFiles(new ArrayList<ModpackIndex.ModpackFile>())
            .setVersionId(null);
        expectedIndex.getDependencies().put(DependencyId.minecraft, "1.20.1");

        ByteArrayOutputStream zipBytesOutputStream = new ByteArrayOutputStream();

        ZipOutputStream zipOutputStream = new ZipOutputStream(zipBytesOutputStream);
        zipOutputStream.putNextEntry(new ZipEntry("modrinth.index.json"));
        zipOutputStream.write(mapper.writeValueAsBytes(expectedIndex));
        zipOutputStream.closeEntry();
        zipOutputStream.close();

        Path modpackPath = tempDir.resolve("testpack.mrpack");
        Path resultsFile = tempDir.resolve("results");

        OutputStream modpackFileOutputStream;
        modpackFileOutputStream = Files.newOutputStream(modpackPath);
        modpackFileOutputStream.write(zipBytesOutputStream.toByteArray());
        modpackFileOutputStream.close();
        zipBytesOutputStream.close();

        SharedFetchArgs fetchArgs = new SharedFetchArgs();
        ModrinthApiClient apiClient = new ModrinthApiClient(
            wm.getHttpBaseUrl(), "install-modrinth-modpack",
            fetchArgs.options());

        ModrinthPackInstaller installerUT = new ModrinthPackInstaller(
            apiClient, fetchArgs, modpackPath, tempDir, resultsFile, false);

        ModpackIndex actualIndex = installerUT.processModpack().block();

        assertNotNull(actualIndex);
        assertEquals(expectedIndex, actualIndex);
    }
}
