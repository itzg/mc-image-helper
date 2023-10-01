package me.itzg.helpers.forge;

import static me.itzg.helpers.http.Fetch.fetch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.model.PromotionsSlim;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class ForgeInstaller {

    public static final String LATEST = "latest";
    public static final String RECOMMENDED = "recommended";

    private static final Pattern RESULT_INFO = Pattern.compile(
        "Exec:\\s+(?<exec>.+)"
            + "|The server installed successfully, you should now be able to run the file (?<universalJar>.+)");
    public static final String MANIFEST_ID = "forge";

    private static final Pattern OLD_FORGE_ID_VERSION = Pattern.compile("forge(.+)", Pattern.CASE_INSENSITIVE);

    private final String filesBaseUrl;
    private final String mavenRepoUrl;

    public ForgeInstaller(String filesBaseUrl, String mavenRepoUrl) {
        this.filesBaseUrl = filesBaseUrl;
        this.mavenRepoUrl = mavenRepoUrl;
    }

    @AllArgsConstructor
    private static class VersionPair {
        String minecraft;
        String forge;
    }

    /**
     * @param forgeInstaller when non-null, specifies a provided installer to use
     */
    public void install(String minecraftVersion, String forgeVersion,
        @NonNull Path outputDir,
        @Nullable Path resultsFile,
        boolean forceReinstall,
        Path forgeInstaller
    ) {
        final ForgeManifest prevManifest;
        try {
            prevManifest = loadManifest(outputDir);
        } catch (IOException e) {
            throw new GenericException("Failed to load existing forge manifest", e);
        }

        final PromotionsSlim promotionsSlim = loadPromotions();
        if (promotionsSlim.getPromos().isEmpty()) {
            throw new GenericException("No versions were available in Forge promotions");
        }

        final String resolvedMinecraftVersion;
        final String resolvedForgeVersion;
        if (forgeInstaller == null) {
            resolvedMinecraftVersion = resolveMinecraftVersion(minecraftVersion, promotionsSlim);
            try {
                resolvedForgeVersion = resolveForgeVersion(resolvedMinecraftVersion, forgeVersion, promotionsSlim);
            } catch (IOException e) {
                throw new GenericException("Failed to resolve forge version", e);
            }
        }
        else {
            final VersionPair versions;
            try {
                versions = extractVersion(forgeInstaller);
            } catch (IOException e) {
                throw new GenericException("Failed to extract version from provided installer file", e);
            }
            if (versions == null) {
                throw new GenericException("Failed to locate version from provided installer file");
            }
            resolvedMinecraftVersion = versions.minecraft;
            resolvedForgeVersion = versions.forge;
        }

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
                Objects.equals(prevManifest.getMinecraftVersion(), resolvedMinecraftVersion) &&
                Objects.equals(prevManifest.getForgeVersion(), resolvedForgeVersion)
            ) {
                log.info("Forge version {} for minecraft version {} is already installed",
                    resolvedForgeVersion, resolvedMinecraftVersion
                );
                needsInstall = false;
            } else {
                log.info("Re-installing Forge due to version change from MC {}/Forge {} to MC {}/Forge {}",
                    prevManifest.getMinecraftVersion(), prevManifest.getForgeVersion(),
                    resolvedMinecraftVersion, resolvedForgeVersion);
                needsInstall = true;
            }
        }
        else {
            needsInstall = true;
        }

        final ForgeManifest newManifest;
        if (needsInstall) {
            if (forgeInstaller == null) {
                newManifest = downloadAndInstall(resolvedMinecraftVersion, resolvedForgeVersion, outputDir);
            }
            else {
                newManifest = installUsingExisting(resolvedMinecraftVersion, resolvedForgeVersion, outputDir, forgeInstaller);
            }

            Manifests.save(outputDir, MANIFEST_ID, newManifest);
        }
        else {
            newManifest = null;
        }

        if (resultsFile != null && (newManifest != null || prevManifest != null)) {
            try {
                populateResultsFile(
                    resultsFile, (newManifest != null ? newManifest : prevManifest).getServerEntry(),
                    resolvedMinecraftVersion
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to populate results file", e);
            }
        }
    }

    private VersionPair extractVersion(Path forgeInstaller) throws IOException {

        // Extract version from installer jar's version.json file
        // where top level "id" field is used

        final VersionPair fromVersionJson = IoStreams.readFileFromZip(forgeInstaller, "version.json", inputStream -> {
            final ObjectNode parsed = ObjectMappers.defaultMapper()
                .readValue(inputStream, ObjectNode.class);

            final String id = parsed.get("id").asText("");

            final String[] idParts = id.split("-");
            if (idParts.length != 3 || !idParts[1].equals("forge")) {
                throw new GenericException("Unexpected format of id from Forge installer's version.json: " + id);
            }

            return new VersionPair(idParts[0], idParts[2]);
        });
        if (fromVersionJson != null) {
            return fromVersionJson;
        }

        return IoStreams.readFileFromZip(forgeInstaller, "install_profile.json", inputStream -> {
            final ObjectNode parsed = ObjectMappers.defaultMapper()
                .readValue(inputStream, ObjectNode.class);

            final JsonNode idNode = parsed.path("versionInfo").path("id");
            if (idNode.isTextual()) {
                final String[] idParts = idNode.asText().split("-");

                if (idParts.length >= 2) {
                    final Matcher m = OLD_FORGE_ID_VERSION.matcher(idParts[1]);
                    if (m.matches()) {
                        if (m.group(1).equals(idParts[0])) {
                            // such as 1.11.2-forge1.11.2-13.20.1.2588
                            return new VersionPair(idParts[0], idParts[2]);
                        }
                        else {
                            // such as 1.7.10-Forge10.13.4.1614-1.7.10
                            return new VersionPair(idParts[0], m.group(1));
                        }
                    }
                    else {
                        throw new GenericException("Unexpected format of id from Forge installer's install_profile.json: " + idNode.asText());
                    }
                }
                else {
                    throw new GenericException("Unexpected format of id from Forge installer's install_profile.json: " + idNode.asText());
                }
            }
            else {
                throw new GenericException("install_profile.json seems to be missing versionInfo.id");

            }
        });
    }

    private ForgeManifest loadManifest(Path outputDir) throws IOException {
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
            Manifests.save(outputDir, MANIFEST_ID, converted);

            return converted;
        }

        // otherwise, load the new way
        return Manifests.load(outputDir, MANIFEST_ID, ForgeManifest.class);
    }

    private ForgeManifest installUsingExisting(String minecraftVersion, String forgeVersion, Path outputDir, Path forgeInstaller) {
        final InstallResults results = install(forgeInstaller, outputDir, minecraftVersion, forgeVersion);

        return ForgeManifest.builder()
            .timestamp(Instant.now())
            .minecraftVersion(minecraftVersion)
            .forgeVersion(forgeVersion)
            .serverEntry(Manifests.relativize(outputDir, results.getEntryFile()))
            .build();
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

    private ForgeManifest downloadAndInstall(String minecraftVersion, String forgeVersion, Path outputDir) {
        final Path installerJar = downloadInstaller(outputDir, minecraftVersion, forgeVersion);

        try {
            final InstallResults results = install(installerJar, outputDir, minecraftVersion, forgeVersion);

            return ForgeManifest.builder()
                .timestamp(Instant.now())
                .minecraftVersion(minecraftVersion)
                .forgeVersion(forgeVersion)
                .serverEntry(results.getEntryFile().toString())
                .build();
        } finally {
            try {
                log.debug("Deleting installer jar {}", installerJar);
                Files.delete(installerJar);
            } catch (IOException e) {
                log.warn("Failed to delete installer jar {}", installerJar);
            }
        }
    }

    @Data
    static class InstallResults {
        private Path entryFile;
    }

    /**
     *
     */
    private InstallResults install(Path installerJar, Path outputDir, String minecraftVersion, String forgeVersion) {
        log.info("Running Forge installer. This might take a while...");

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

            return new InstallResults()
                .setEntryFile(entryFile);
        } catch (IOException e) {
            throw new RuntimeException("Trying to run installer", e);
        }
    }

    private Path resolveInstallerLog(Path outputDir, Path installerJar) {
        return outputDir.resolve(installerJar.getFileName() + ".log");
    }

    private Path downloadInstaller(Path outputDir, String minecraftVersion, String forgeVersion) {
        log.info("Downloading Forge installer {} for Minecraft {}", forgeVersion, minecraftVersion);

        final Path installerJar = outputDir.resolve(String.format("forge-installer-%s-%s",
            minecraftVersion, forgeVersion
        ) +".jar");

        boolean success = false;
        // every few major versions Forge would chane their version qualifier scheme :(
        for (final String installerUrlVersion : new String[]{
            String.join("-", minecraftVersion, forgeVersion),
            String.join("-", minecraftVersion, forgeVersion, minecraftVersion),
            String.join("-", minecraftVersion, forgeVersion, "mc172")
        }) {
            try {
                fetch(Uris.populateToUri(
                    mavenRepoUrl
                        + "/net/minecraftforge/forge/{version}/forge-{version}-installer.jar",
                    installerUrlVersion, installerUrlVersion
                ))
                    .userAgentCommand("forge")
                    .toFile(installerJar)
                    .skipExisting(true)
                    .acceptContentTypes(Collections.singletonList("application/java-archive"))
                    .execute();
                success = true;
                break;
            } catch (FailedRequestException e){
                if (e.getStatusCode() != 404) {
                    throw new RuntimeException("Trying to download forge installer", e);
                }
            }
            catch (IOException e) {
                throw new RuntimeException("Trying to download forge installer", e);
            }
        }

        if (!success) {
            throw new GenericException("Failed to locate forge installer");
        }

        return installerJar;
    }

    @Data
    @AllArgsConstructor
    static class PromoEntry {

        String mcVersion;
        String promo;
        String forgeVersion;
    }

    private String resolveForgeVersion(String minecraftVersion, String forgeVersion, PromotionsSlim promotionsSlim) throws IOException {
        final String normalized = forgeVersion.toLowerCase();
        if (!normalized.equals(LATEST) && !normalized.equals(RECOMMENDED)) {
            return forgeVersion;
        }

        final Map<String, String> options = promotionsSlim.getPromos().entrySet().stream()
            .map(ForgeInstaller::parsePromoEntry)
            // narrow to just applicable minecraft version
            .filter(entry -> entry.getMcVersion().equals(minecraftVersion))
            // ...and arrive at a map that has one or two entries for latest and/or recommended
            .collect(Collectors.toMap(
                PromoEntry::getPromo,
                PromoEntry::getForgeVersion
            ));

        log.debug("Narrowed forge versions to {} and looking for {}", options, normalized);

        if (!options.isEmpty()) {
            final String result = options.get(normalized);
            if (result != null) {
                return result;
            } else {
                // ...otherwise need to fall back to what we have
                return options.values().iterator().next();
            }
        }
        else {
            throw new InvalidParameterException(String.format("Minecraft version %s not available from Forge", minecraftVersion));
        }
    }

    private PromotionsSlim loadPromotions() {
        return fetch(URI.create(filesBaseUrl
            + "/net/minecraftforge/forge/promotions_slim.json"))
            .userAgentCommand("forge")
            .toObject(PromotionsSlim.class)
            .execute();
    }

    private String resolveMinecraftVersion(String minecraftVersion, PromotionsSlim promotionsSlim) {
        if (minecraftVersion == null || minecraftVersion.equalsIgnoreCase(LATEST)) {
            return promotionsSlim.getPromos().entrySet().stream()
                .map(ForgeInstaller::parsePromoEntry)
                // pick off the last entry, where order is significant since JSON parsing retains ordering
                .reduce((lhs, rhs) -> rhs)
                .map(promoEntry -> promoEntry.mcVersion)
                .orElseThrow(() -> new GenericException("No versions were available in Forge promotions"));
        } else {
            return minecraftVersion;
        }
    }

    private static PromoEntry parsePromoEntry(Entry<String, String> entry) {
        // each entry is like
        // "1.19-recommended": "41.1.0"
        final String[] keyParts = entry.getKey().split("-", 2);
        return new PromoEntry(keyParts[0], keyParts[1].toLowerCase(), entry.getValue());
    }
}
