package me.itzg.helpers.cleanroom;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import org.jetbrains.annotations.Nullable;
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

@Slf4j
public class CleanroomInstaller {

    private static final Pattern RESULT_INFO = Pattern.compile(
        "Fetching Cleanroom \\s+(?<version>.+)");

    private final CleanroomInstallerResolver installerResolver;

    public CleanroomInstaller(CleanroomInstallerResolver installerResolver) {
        this.installerResolver = installerResolver;
    }

    public void install(
        @NonNull Path outputDir,
        @Nullable Path resultsFile,
        boolean forceReinstall
    ) {
        final CleanroomManifest prevManifest;
        try {
            prevManifest = loadManifest(outputDir);
        } catch (IOException e) {
            throw new GenericException("Failed to load existing cleanroom manifest", e);
        }

        final CleanroomVersion resolved = installerResolver.resolve(prevManifest, null);
        if (resolved == null) {
            throw new InvalidParameterException("Unable to find suitable version for " +
                installerResolver.getDescription());
        }
        log.debug("Resolved installer version={}", resolved.installerVersion());

        final boolean needsInstall;
        if (forceReinstall) {
            needsInstall = true;
        }
        else if (prevManifest != null) {
            if (!serverEntryExists(outputDir, prevManifest.getServerEntry())) {
                log.warn("Server entry for Cleanroom {} is missing. Re-installing.",
                    prevManifest.getCleanroomVersion()
                );
                needsInstall = true;
            }
            else if (
                    Objects.equals(prevManifest.getCleanroomVersion(), resolved.cleanroomVersion())
            ) {
                log.info("Cleanroom version {} for minecraft version 1.12.2 is already installed",
                    resolved.cleanroomVersion()
                );
                needsInstall = false;
            } else {
                log.info("Re-installing Cleanroom due to version change {} to {}",
                    prevManifest.getCleanroomVersion(), resolved);
                needsInstall = true;
            }
        }
        else {
            needsInstall = true;
        }

        final CleanroomManifest newManifest;
        if (needsInstall) {
            final Path cleanroomInstallerJar = installerResolver.download(resolved, outputDir);

            try {
                newManifest = install(cleanroomInstallerJar, outputDir, resolved);
            } finally {
                installerResolver.cleanup(cleanroomInstallerJar);
            }

            Manifests.save(outputDir, CleanroomManifest.manifestId, newManifest);
        }
        else {
            newManifest = null;
        }

        if (resultsFile != null && (newManifest != null || prevManifest != null)) {
            try {
                populateResultsFile(
                    resultsFile, (newManifest != null ? newManifest : prevManifest).getServerEntry(),
                    resolved
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to populate results file", e);
            }
        }

    }

    private boolean serverEntryExists(@NonNull Path outputDir, String serverEntry) {
        return (serverEntry.startsWith("/") && Files.exists(Paths.get(serverEntry)))
            || Files.exists(outputDir.resolve(serverEntry));
    }

    private CleanroomManifest loadManifest(Path outputDir) throws IOException {
        // new manifest, don't need to load legacy
        return Manifests.load(outputDir, CleanroomManifest.manifestId, CleanroomManifest.class);
    }

    private void populateResultsFile(Path resultsFile, String serverEntry, CleanroomVersion cleanroomVersion) throws IOException {
        log.debug("Populating results file {}", resultsFile);

        try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
            results.write("SERVER", serverEntry);
            results.write("FAMILY", "FORGE");
            results.writeVersion(cleanroomVersion.cleanroomVersion());
            results.writeType("CLEANROOM");
        }
    }

    /**
     *
     */
    private CleanroomManifest install(Path installerJar, Path outputDir, CleanroomVersion cleanroomVersion) {
        log.info("Installing Cleanroom {} using installer {}. This might take a while...",
            cleanroomVersion.cleanroomVersion(), cleanroomVersion.installerVersion()
        );

        try {
            final Process process = new ProcessBuilder(
                "java", "-jar", installerJar.toAbsolutePath().toString(), "server", "--log-file", "./cleanroom-install.log"
            )
                .directory(outputDir.toFile())
                .redirectError(Redirect.INHERIT)
                .start();

            final BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            String loaderVersion = null;
            String line;
            while ((line = reader.readLine()) != null) {
                final Matcher m = RESULT_INFO.matcher(line);
                if (m.matches()) {
                    final String exec = m.group("version");
                    if (exec != null) {
                        loaderVersion = exec;
                        log.debug("Observed Cleanroom loader version from \"Fetching\" line: {}", loaderVersion);
                    }
                }
            }

            final Path installerLog = outputDir.resolve(installerJar.getFileName() + ".log");
            try {
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    if (Files.exists(installerLog)) {
                        Files.copy(installerLog, System.err);
                    }
                    throw new GenericException("Cleanroom installer failed with exit code " + exitCode);
                }
            } catch (InterruptedException e) {
                throw new GenericException("Interrupted waiting for cleanroom installer", e);
            }

            if (loaderVersion == null) {
                throw new GenericException("Unable to identify Cleanroom Loader version from installer console output");
            }

            // Cleanroom installer that doesn't report entry point in logs
            Path entryFile = outputDir.resolve("run.sh");
            if (Files.exists(entryFile)) {
                entryFile = entryFile.toAbsolutePath();
            }
            else {
                throw new GenericException("Unable to locate Cleanroom start script");
            }
            log.debug("Discovered entry file: {}", entryFile);

            if (Files.exists(installerLog)) {
                log.debug("Deleting Cleanroom installer log at {}", installerLog);
                Files.delete(installerLog);
            }

            final String relativeServerEntry;
            if (outputDir.isAbsolute() == entryFile.isAbsolute()) {
                relativeServerEntry = Manifests.relativize(outputDir, entryFile);
            }
            else {
                relativeServerEntry = entryFile.toString();
            }

            return CleanroomManifest.builder()
                .timestamp(Instant.now())
                .installerVersion(cleanroomVersion.installerVersion())
                .cleanroomVersion(loaderVersion)
                .serverEntry(
                    relativeServerEntry
                )
                .build();

        } catch (IOException e) {
            throw new RuntimeException("Trying to run installer", e);
        }
    }
}
