package me.itzg.helpers.cleanroom;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.mvn.MavenMetadata;
import me.itzg.helpers.mvn.MavenRepoApi;
import org.apache.maven.artifact.versioning.ComparableVersion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Setter
public class CleanroomInstaller
{
    public static final String LATEST = "latest";

    private static final Pattern RESULT_INFO = Pattern.compile("Fetching Cleanroom\\s+(?<version>.+)");
    private static final Pattern LEGACY_INSTALLER_VERSION = Pattern.compile("cleanroom-(.+)", Pattern.CASE_INSENSITIVE);

    private Path outputDirectory;
    private Path resultsFile;
    private String mavenUrl;
    private SharedFetch.Options sharedFetchOptions;
    private boolean forceReinstall;
    private String installerVersion;
    private String loaderVersion;
    private boolean dryRun;

    @Setter(AccessLevel.NONE)
    private boolean lazyLoadPrevManifest;
    @Setter(AccessLevel.NONE)
    private CleanroomManifest prevManifest;

    private CleanroomManifest prevManifest() {
        if (!lazyLoadPrevManifest) {
            prevManifest = Manifests.load(this.outputDirectory, CleanroomManifest.manifestId, CleanroomManifest.class);
            lazyLoadPrevManifest = true;
        }
        return prevManifest;
    }

    /**
     * Installing using provided URL. Usage for remote installer.
     * @param installerUrl url to installer
     * @return if success
     */
    public boolean install(URI installerUrl) throws IOException {
        final Path installerPath;

        log.info("Install Cleanroom using remote source: {}", installerUrl.toString());
        try (SharedFetch sharedFetch = Fetch.sharedFetch("cleanroom", this.sharedFetchOptions)) {
            installerPath = sharedFetch.fetch(installerUrl)
                .toDirectory(this.outputDirectory)
                .skipUpToDate(true)
                .handleStatus(Fetch.loggingDownloadStatusHandler(log))
                .assemble()
                .block();
        }

        if (installerPath == null || !Files.exists(installerPath)) {
            throw new GenericException("Failed to download Cleanroom installer");
        }

        log.info("Succeed to download Cleanroom installer {}", installerPath);
        return install(installerPath);
    }


    /**
     * Installing using provided path. Usage for local or downloaded installer.
     * @param installerPath path to installer
     * @return if success
     */
    public boolean install(Path installerPath) {
        final CleanroomManifest prevManifest = this.prevManifest();

        final String legacyLoaderVersion;

        try {
            legacyLoaderVersion = IoStreams.readFileFromZip(installerPath.toAbsolutePath(),
                "version.json", CleanroomInstaller::extractFromVersionJson);
        } catch (IOException e) {
            throw new GenericException("Error while reading version.json from installer", e);
        }

        final boolean isLegacyInstaller = legacyLoaderVersion != null;

        // If not forced, check condition first
        if (!this.forceReinstall && prevManifest != null) {
            // installerVersion null mean server is legacy
            if (prevManifest.getInstallerVersion() == null && prevManifest.getLoaderVersion().equals(legacyLoaderVersion)// legacy
                || prevManifest.getLoaderVersion().equals(this.loaderVersion)) {                                         // new
                // check if missing entry file
                if (serverEntryExists(this.outputDirectory, prevManifest.getServerEntry())) {
                    log.info("Cleanroom loader version {} is already installed", prevManifest.getLoaderVersion());
                    return true;
                }
                else {
                    log.warn("Server entry for Cleanroom {} is missing. Re-installing.", prevManifest.getLoaderVersion());
                }
            }

            log.info("Re-installing Cleanroom due to version change from {} to {}",
                prevManifest.getLoaderVersion(), isLegacyInstaller ? legacyLoaderVersion : this.loaderVersion);
        }

        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-jar");
        args.add(installerPath.toAbsolutePath().toString());

        if (isLegacyInstaller) {
            args.add("--installServer"); // legacy installer
        }
        else {
            // using new installer
            args.add("server");
            if (this.loaderVersion != null && !LATEST.equals(this.loaderVersion)) {
                args.add("-v");
                args.add(this.loaderVersion);
            }
            if (this.forceReinstall) {
                args.add("--force");
            }
            if (this.dryRun) {
                args.add("--dry-run");
            }
            //logging
            args.add("--log-file");
            args.add(installerPath.getFileName().toString() + ".log");
        }

        try {
            log.info("Running installer for Cleanroom {}. This might take a while...",
                isLegacyInstaller ? legacyLoaderVersion : this.loaderVersion);
            final Process process = new ProcessBuilder(args)
                .directory(this.outputDirectory.toFile())
                .redirectError(Redirect.INHERIT)
                .start();

            String loaderVersion = isLegacyInstaller ? legacyLoaderVersion : this.loaderVersion;

            if (loaderVersion == null || LATEST.equals(loaderVersion)) {
                final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

                String line;
                while ((line = reader.readLine()) != null) {
                    final Matcher m = RESULT_INFO.matcher(line);
                    if (m.matches()) {
                        final String exec = m.group("version");
                        if (exec != null) {
                            loaderVersion = exec;
                            log.debug("Observed Cleanroom loader version from \"Fetching\" line: {}", loaderVersion);
                            reader.close();
                            break; // we do not need to read anymore
                        }
                    }
                }
            }

            final Path installerLog = outputDirectory.resolve(installerPath.getFileName() + ".log");
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

            if (loaderVersion == null || LATEST.equals(loaderVersion)) {
                throw new GenericException("Unable to identify Cleanroom Loader version from installer console output");
            }

            Path entryFile = outputDirectory.resolve("run.sh");
            if (!dryRun) {
                if (Files.exists(entryFile)) {
                    entryFile = entryFile.toAbsolutePath();
                }
                else {
                    entryFile = outputDirectory.resolve("cleanroom-" + loaderVersion + ".jar");
                    if (Files.exists(entryFile)) {
                        entryFile = entryFile.toAbsolutePath();
                    }
                    else {
                        throw new GenericException("Unable to locate Cleanroom start entry file");
                    }
                }
                log.debug("Discovered entry file: {}", entryFile);
            }
            if (Files.exists(installerLog)) {
                log.debug("Deleting Cleanroom installer log at {}", installerLog);
                Files.delete(installerLog);
            }

            final String relativeServerEntry;
            if (this.outputDirectory.isAbsolute() == entryFile.isAbsolute()) {
                relativeServerEntry = Manifests.relativize(this.outputDirectory, entryFile);
            }
            else {
                relativeServerEntry = entryFile.toString();
            }

            CleanroomManifest newManifest = CleanroomManifest.builder()
                .timestamp(Instant.now())
                .installerVersion(isLegacyInstaller ? null : this.installerVersion)
                .loaderVersion(loaderVersion)
                .serverEntry(relativeServerEntry)
                .build();

            Manifests.save(this.outputDirectory, CleanroomManifest.manifestId, newManifest);

            if (resultsFile != null && (newManifest != null || prevManifest != null)) {
                try {
                    populateResultsFile(
                        resultsFile, (newManifest != null ? newManifest : prevManifest).getServerEntry(),
                        loaderVersion
                    );
                } catch (IOException e) {
                    throw new RuntimeException("Failed to populate results file", e);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Trying to run installer", e);
        }

        return true;
    }

    /**
     * Install by download from Cleanroom Maven. Version required.
     * @return if success
     */
    public boolean install() {
        final MavenRepoApi mavenRepoApi;
        try (SharedFetch sharedFetch = Fetch.sharedFetch("cleanroom", this.sharedFetchOptions)) {
            mavenRepoApi = new MavenRepoApi(this.mavenUrl, sharedFetch);

            final MavenMetadata metadata = mavenRepoApi.fetchMetadata(CleanroomManifest.mvnGroupId, CleanroomManifest.mvnArtifactId)
                .block();

            if (metadata == null) {
                throw new GenericException("Unable to resolve Cleanroom metadata");
            }

            final String result = metadata.getVersioning().getVersion().stream()
                .filter(s -> s.matches("0\\.[0-9]*\\.[0-9]*"))
                .filter(s -> this.installerVersion == null || LATEST.equals(this.installerVersion) || s.equals(this.installerVersion))
                // pick the highest version from a or b
                .reduce((a, b) ->
                    new ComparableVersion(a).compareTo(new ComparableVersion(b)) > 0 ? a : b
                )
                .orElse(null);

            if (result == null) {
                throw new GenericException("Unable to resolve Cleanroom installer version");
            }

            this.setInstallerVersion(result);
            // try to find exist installer
            Path installerPath = outputDirectory.resolve("installer-" + result + ".jar");

            if (!this.forceReinstall && Files.exists(installerPath)) {
                log.warn("Installer {} already exist on server directory", result);
            }
            else {
                log.info("Downloading installer {} for Cleanroom {}", result, this.loaderVersion);
                installerPath = mavenRepoApi.download(this.outputDirectory, CleanroomManifest.mvnGroupId, CleanroomManifest.mvnArtifactId,
                    result, "jar", null).block();
            }

            // this should not happen, but idea don't like so...
            if (installerPath == null) {
                throw new GenericException("Failed to download Cleanroom installer");
            }

            return install(installerPath);
        }
    }

    private boolean serverEntryExists(@NonNull Path outputDir, String serverEntry) {
        return (serverEntry.startsWith("/") && Files.exists(Paths.get(serverEntry)))
            || Files.exists(outputDir.resolve(serverEntry));
    }

    /**
     * Extract version from installer jar's version.json file where top level "id" field is used
     */
    public static String extractFromVersionJson(InputStream versionJsonIn) throws IOException {
        final String id = ObjectMappers.defaultMapper().readValue(versionJsonIn, ObjectNode.class)
            .get("id").asText();
        Matcher m = LEGACY_INSTALLER_VERSION.matcher(id);
        return m.find() ? m.group(1) + "-legacy" : null;
    }

    private void populateResultsFile(Path resultsFile, String serverEntry, String loaderVersion) throws IOException {
        log.debug("Populating results file {}", resultsFile);

        try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
            results.write("SERVER", serverEntry);
            results.write("FAMILY", "FORGE");
            results.writeType("CLEANROOM");
            results.writeVersion(loaderVersion);
        }
    }

}
