package me.itzg.helpers.forge;

import static me.itzg.helpers.http.Fetch.fetch;

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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.model.PromotionsSlim;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;

@Slf4j
public class ForgeInstaller {

    public static final String LATEST = "latest";
    public static final String RECOMMENDED = "recommended";

    private static final Pattern RESULT_INFO = Pattern.compile(
        "Exec:\\s+(?<exec>.+)"
            + "|The server installed successfully, you should now be able to run the file (?<universalJar>.+)");
    public static final String MANIFEST_ID = "forge";

    public void install(String minecraftVersion, String forgeVersion,
        Path outputDir, Path resultsFile,
        boolean forceReinstall,
        Path forgeInstaller
    ) {
        final ForgeManifest prevManifest;
        try {
            prevManifest = loadManifest(outputDir);
        } catch (IOException e) {
            throw new GenericException("Failed to load existing forge manifest", e);
        }

        final String resolvedForgeVersion;
        if (forgeInstaller == null) {
            try {
                resolvedForgeVersion = resolveForgeVersion(minecraftVersion, forgeVersion);
            } catch (IOException e) {
                throw new RuntimeException("Failed to resolve forge version", e);
            }
        }
        else {
            resolvedForgeVersion = forgeInstaller.toString();
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
                Objects.equals(prevManifest.getMinecraftVersion(), minecraftVersion) &&
                Objects.equals(prevManifest.getForgeVersion(), resolvedForgeVersion)
            ) {
                log.info("Forge version {} for minecraft version {} is already installed",
                    resolvedForgeVersion, minecraftVersion
                );
                needsInstall = false;
            } else {
                log.info("Re-installing Forge due to version change from MC {}/Forge {} to MC {}/Forge {}",
                    prevManifest.getMinecraftVersion(), prevManifest.getForgeVersion(),
                    minecraftVersion, resolvedForgeVersion);
                needsInstall = true;
            }
        }
        else {
            needsInstall = true;
        }

        final ForgeManifest newManifest;
        if (needsInstall) {
            if (forgeInstaller == null) {
                newManifest = downloadAndInstall(minecraftVersion, resolvedForgeVersion, outputDir);
            }
            else {
                newManifest = installUsingExisting(minecraftVersion, resolvedForgeVersion, outputDir, forgeInstaller);
            }

            Manifests.save(outputDir, MANIFEST_ID, newManifest);
        }
        else {
            newManifest = null;
        }

        if (resultsFile != null && (newManifest != null || prevManifest != null)) {
            try {
                populateResultsFile(resultsFile, (newManifest != null ? newManifest : prevManifest).getServerEntry());
            } catch (IOException e) {
                throw new RuntimeException("Failed to populate results file", e);
            }
        }
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

    private void populateResultsFile(Path resultsFile, String serverEntry) throws IOException {
        log.debug("Populating results file {}", resultsFile);

        try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
            results.write("SERVER", serverEntry);
            results.write("FAMILY", "FORGE");
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
            final Process process = new ProcessBuilder("java", "-jar", installerJar.toString(), "--installServer")
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

            final Path installerLog = installerJar.getParent().resolve(
                installerJar.getFileName().toString() + ".log"
            );
            try {
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    if (Files.exists(installerLog)) {
                        Files.copy(installerLog, System.err);
                    }
                    throw new RuntimeException("Forge installer failed with exit code " + exitCode);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted waiting for forge installer", e);
            }

            log.debug("Deleting Forge installer log at {}", installerLog);
            Files.delete(installerLog);

            // A 1.12.2 style installer that says nothing useful?
            if (entryFile == null) {
                final Path resolved = outputDir.resolve(String.format("forge-%s-%s.jar", minecraftVersion, forgeVersion));
                if (Files.exists(resolved)) {
                    entryFile = resolved.toAbsolutePath();
                }
                else {
                    throw new RuntimeException("Unable to locate forge server jar");
                }
                log.debug("Discovered entry file: {}", entryFile);
            }

            return new InstallResults()
                .setEntryFile(entryFile);
        } catch (IOException e) {
            throw new RuntimeException("Trying to run installer", e);
        }
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
                    "https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar",
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
            throw new RuntimeException("Failed to locate forge installer");
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

    private String resolveForgeVersion(String minecraftVersion, String forgeVersion) throws IOException {
        final String normalized = forgeVersion.toLowerCase();
        if (!normalized.equals(LATEST) && !normalized.equals(RECOMMENDED)) {
            return forgeVersion;
        }

        final PromotionsSlim promotionsSlim =
            fetch(URI.create("https://files.minecraftforge.net/maven/net/minecraftforge/forge/promotions_slim.json"))
                .userAgentCommand("forge")
                .toObject(PromotionsSlim.class)
                .execute();

        final Map<String, String> options = promotionsSlim.getPromos().entrySet().stream()
            // each entry is like
            // "1.19-recommended": "41.1.0"
            .map(entry -> {
                final String[] keyParts = entry.getKey().split("-", 2);
                return new PromoEntry(keyParts[0], keyParts[1].toLowerCase(), entry.getValue());
            })
            // narrow to just applicable minecraft version
            .filter(entry -> entry.getMcVersion().equals(minecraftVersion))
            // ...and arrive at a map that has one or two entries for latest and/or recommended
            .collect(Collectors.toMap(
                PromoEntry::getPromo,
                PromoEntry::getForgeVersion
            ));

        log.debug("Narrowed forge versions to {} and looking for {}", options, normalized);

        final String result = options.get(normalized);
        if (result != null) {
            return result;
        } else {
            // ...otherwise need to fall back to what we have
            return options.values().iterator().next();
        }
    }
}
