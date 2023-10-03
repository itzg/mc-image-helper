package me.itzg.helpers.forge;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.json.ObjectMappers;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class ForgeInstaller {

    private static final Pattern RESULT_INFO = Pattern.compile(
        "Exec:\\s+(?<exec>.+)"
            + "|The server installed successfully, you should now be able to run the file (?<universalJar>.+)");

    private final InstallerResolver installerResolver;

    public ForgeInstaller(InstallerResolver installerResolver) {
        this.installerResolver = installerResolver;
    }

    public void install(
        @NonNull Path outputDir,
        @Nullable Path resultsFile,
        boolean forceReinstall,
        String variant
    ) {
        final String manifestId = variant.toLowerCase();
        final ForgeManifest prevManifest;
        try {
            prevManifest = loadManifest(outputDir, manifestId);
        } catch (IOException e) {
            throw new GenericException("Failed to load existing forge manifest", e);
        }

        final VersionPair resolved = installerResolver.resolve();
        log.debug("Resolved installer version={}", resolved);

        final boolean needsInstall;
        if (forceReinstall) {
            needsInstall = true;
        }
        else if (prevManifest != null) {
            if (!Files.exists(Paths.get(prevManifest.getServerEntry()))) {
                log.warn("Server entry for Minecraft {} Forge {} is missing. Re-installing.",
                    prevManifest.getMinecraftVersion(), prevManifest.getForgeVersion()
                    );
                needsInstall = true;
            }
            else if (
                Objects.equals(prevManifest.getMinecraftVersion(), resolved.minecraft) &&
                Objects.equals(prevManifest.getForgeVersion(), resolved.forge)
            ) {
                log.info("{} version {} for minecraft version {} is already installed",
                    variant, resolved.forge, resolved.minecraft
                );
                needsInstall = false;
            } else {
                log.info("Re-installing Forge due to version change from MC {}/Forge {} to MC {}/Forge {}",
                    prevManifest.getMinecraftVersion(), prevManifest.getForgeVersion(),
                    resolved.minecraft, resolved.forge);
                needsInstall = true;
            }
        }
        else {
            needsInstall = true;
        }

        final ForgeManifest newManifest;
        if (needsInstall) {
            final Path forgeInstallerJar = installerResolver.download(resolved.minecraft, resolved.forge, outputDir);

            try {
                newManifest = install(forgeInstallerJar, outputDir, resolved.minecraft, variant, resolved.forge);

            } finally {

                installerResolver.cleanup(forgeInstallerJar);
            }

            Manifests.save(outputDir, manifestId, newManifest);
        }
        else {
            newManifest = null;
        }

        if (resultsFile != null && (newManifest != null || prevManifest != null)) {
            try {
                populateResultsFile(
                    resultsFile, (newManifest != null ? newManifest : prevManifest).getServerEntry(),
                    resolved.minecraft
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to populate results file", e);
            }
        }
    }

    private ForgeManifest loadManifest(Path outputDir, String variant) throws IOException {
        // First check for and retrofit legacy manifest format
        final Path legacyFile = outputDir.resolve(LegacyManifest.FILENAME);
        if (Files.exists(legacyFile)) {
            final LegacyManifest legacyManifest = ObjectMappers.defaultMapper()
                .readValue(legacyFile.toFile(), LegacyManifest.class);


            final ForgeManifest converted = ForgeManifest.builder()
                .serverEntry(legacyManifest.getServerEntry())
                .minecraftVersion(legacyManifest.getMinecraftVersion())
                .forgeVersion(legacyManifest.getForgeVersion())
                .build();

            // switch it out
            Files.delete(legacyFile);
            Manifests.save(outputDir, variant, converted);

            return converted;
        }

        // otherwise, load the new way
        return Manifests.load(outputDir, variant, ForgeManifest.class);
    }

    private void populateResultsFile(Path resultsFile, String serverEntry, String minecraftVersion) throws IOException {
        log.debug("Populating results file {}", resultsFile);

        try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
            results.write("SERVER", serverEntry);
            results.write("FAMILY", "FORGE");
            results.writeVersion(minecraftVersion);
            results.writeType("FORGE");
        }
    }

    /**
     *
     */
    private ForgeManifest install(Path installerJar, Path outputDir, String minecraftVersion, String variant, String forgeVersion) {
        log.info("Running {} installer. This might take a while...", variant);

        try {
            final Process process = new ProcessBuilder(
                "java", "-jar", installerJar.toAbsolutePath().toString(), "--installServer"
            )
                .directory(outputDir.toFile())
                .redirectError(Redirect.INHERIT)
                .start();

            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            Path entryFile = null;
            String line;
            while ((line = reader.readLine()) != null) {
                final Matcher m = RESULT_INFO.matcher(line);
                if (m.matches()) {
                    final String exec = m.group("exec");
                    if (exec != null) {
                        entryFile = Paths.get(exec);
                        log.debug("Observed entry file from exec line: {}", entryFile);
                    } else {
                        final String universalJar = m.group("universalJar");
                        if (universalJar != null) {
                            entryFile = outputDir.resolve(universalJar);
                            log.debug("Observed entry file from universal jar log line: {}", entryFile);
                        }
                    }
                }
            }

            final Path installerLog = resolveInstallerLog(outputDir, installerJar);
            try {
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    if (Files.exists(installerLog)) {
                        Files.copy(installerLog, System.err);
                    }
                    throw new GenericException("Forge installer failed with exit code " + exitCode);
                }
            } catch (InterruptedException e) {
                throw new GenericException("Interrupted waiting for forge installer", e);
            }

            if (Files.exists(installerLog)) {
                log.debug("Deleting Forge installer log at {}", installerLog);
                Files.delete(installerLog);
            }

            // A 1.12.2 style installer that says nothing useful?
            if (entryFile == null) {
                final Path resolved = outputDir.resolve(String.format("forge-%s-%s.jar", minecraftVersion, forgeVersion));
                if (Files.exists(resolved)) {
                    entryFile = resolved.toAbsolutePath();
                }
                else {
                    throw new GenericException("Unable to locate forge server jar");
                }
                log.debug("Discovered entry file: {}", entryFile);
            }

            final String relativeServerEntry;
            if (outputDir.isAbsolute() == entryFile.isAbsolute()) {
                relativeServerEntry = Manifests.relativize(outputDir, entryFile);
            }
            else {
                relativeServerEntry = entryFile.toString();
            }

            return ForgeManifest.builder()
                .timestamp(Instant.now())
                .minecraftVersion(minecraftVersion)
                .forgeVersion(forgeVersion)
                .serverEntry(
                    relativeServerEntry
                )
                .build();

        } catch (IOException e) {
            throw new RuntimeException("Trying to run installer", e);
        }
    }

    private Path resolveInstallerLog(Path outputDir, Path installerJar) {
        return outputDir.resolve(installerJar.getFileName() + ".log");
    }
}
