package me.itzg.helpers.forge;

import static me.itzg.helpers.http.Fetch.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.FileTreeSnapshot;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.Manifest.ManifestBuilder;
import me.itzg.helpers.forge.model.PromotionsSlim;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import org.apache.hc.client5.http.HttpResponseException;

@Slf4j
public class ForgeInstaller {

    public static final String LATEST = "latest";
    public static final String RECOMMENDED = "recommended";

    private static final Pattern RESULT_INFO = Pattern.compile(
        "Exec:\\s+(?<exec>.+)"
            + "|The server installed successfully, you should now be able to run the file (?<universalJar>.+)");

    public void install(String minecraftVersion, String forgeVersion,
        Path outputDir, Path resultsFile,
        boolean forgeReinstall,
        Path forgeInstaller
    ) {
        final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

        final Path manifestPath = outputDir.resolve(Manifest.FILENAME);
        final Manifest oldManifest;
        try {
            oldManifest = Files.exists(manifestPath) ?
                objectMapper.readValue(manifestPath.toFile(), Manifest.class)
                : null;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load existing manifest", e);
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

        boolean needsInstall = true;
        if (oldManifest != null) {
            if (!forgeReinstall &&
                Objects.equals(oldManifest.getMinecraftVersion(), minecraftVersion) &&
                Objects.equals(oldManifest.getForgeVersion(), resolvedForgeVersion)) {

                log.info("Forge version {} for minecraft version {} is already installed",
                    resolvedForgeVersion, minecraftVersion
                );
                needsInstall = false;
            } else {
                log.info("Removing previously installed files due to version change from MC {}/Forge {} to MC {}/Forge {}",
                    oldManifest.getMinecraftVersion(), oldManifest.getForgeVersion(),
                    minecraftVersion, resolvedForgeVersion);
                removeOldFiles(outputDir, oldManifest.getFiles());
            }
        }

        final Manifest newManifest;
        if (needsInstall) {
            if (forgeInstaller == null) {
                newManifest = downloadAndInstall(minecraftVersion, resolvedForgeVersion, outputDir);
            }
            else {
                newManifest = installUsingExisting(minecraftVersion, resolvedForgeVersion, outputDir, forgeInstaller);
            }
        }
        else {
            newManifest = null;
        }

        if (resultsFile != null && (newManifest != null || oldManifest != null)) {
            try {
                populateResultsFile(resultsFile, newManifest != null ? newManifest : oldManifest);
            } catch (IOException e) {
                throw new RuntimeException("Failed to populate results file", e);
            }
        }

        if (newManifest != null) {
            try {
                objectMapper.writeValue(manifestPath.toFile(), newManifest);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write manifest", e);
            }
        }
    }

    private Manifest installUsingExisting(String minecraftVersion, String forgeVersion, Path outputDir, Path forgeInstaller) {
        final Manifest newManifest;
        final ManifestBuilder manifestBuilder = Manifest.builder()
            .timestamp(Instant.now())
            .minecraftVersion(minecraftVersion)
            .forgeVersion(forgeVersion);

        install(forgeInstaller, outputDir, minecraftVersion, forgeVersion, manifestBuilder);
        newManifest = manifestBuilder.build();
        return newManifest;
    }

    private void removeOldFiles(Path dir, Set<String> files) {
        log.debug("Removing old files");
        for (final String file : files) {
            try {
                final Path filePath = dir.resolve(file);
                log.debug("Deleting {}", filePath);
                Files.delete(filePath);
            }
            catch (NoSuchFileException e) {
                log.debug("Skipping deletion of non-existent file {}", file);
            }
            catch (IOException e) {
                log.warn("Failed to delete old file {} in {}", file, dir, e);
            }
        }
    }

    private void populateResultsFile(Path resultsFile, Manifest manifest) throws IOException {
        log.debug("Populating results file {}", resultsFile);

        try (ResultsFileWriter results = new ResultsFileWriter(resultsFile)) {
            results.write("SERVER", manifest.getServerEntry());
            results.write("FAMILY", "FORGE");
        }
    }

    private Manifest downloadAndInstall(String minecraftVersion, String forgeVersion, Path outputDir) {
        final ManifestBuilder manifestBuilder = Manifest.builder()
            .timestamp(Instant.now())
            .minecraftVersion(minecraftVersion)
            .forgeVersion(forgeVersion);

        final Path installerJar = downloadInstaller(minecraftVersion, forgeVersion);

        try {
            install(installerJar, outputDir, minecraftVersion, forgeVersion, manifestBuilder);
        } finally {
            try {
                log.debug("Deleting installer jar {}", installerJar);
                Files.delete(installerJar);
            } catch (IOException e) {
                log.warn("Failed to delete installer jar {}", installerJar);
            }
        }

        return manifestBuilder.build();
    }

    /**
     *
     */
    private void install(Path installerJar, Path outputDir, String minecraftVersion, String forgeVersion,
        ManifestBuilder manifestBuilder
    ) {
        log.debug("Gathering snapshot of {} before installer", outputDir);
        final FileTreeSnapshot snapshotBefore;
        try {
            snapshotBefore = FileTreeSnapshot.takeSnapshot(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Tried to snapshot files before forge installer", e);
        }

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

            try {
                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("Forge installer failed with exit code " + exitCode);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted waiting for forge installer", e);
            }

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

            manifestBuilder.serverEntry(entryFile.toString());

            log.debug("Gathering and comparing snapshot after installer");
            manifestBuilder.files(
                snapshotBefore.findNewFiles()
            );
        } catch (IOException e) {
            throw new RuntimeException("Trying to run installer", e);
        }
    }

    private Path downloadInstaller(String minecraftVersion, String forgeVersion) {
        log.info("Downloading Forge installer {} for Minecraft {}", forgeVersion, minecraftVersion);

        final Path installerJar;
        try {
            installerJar = Files.createTempFile("forge-installer-", ".jar");
        } catch (IOException e) {
            throw new RuntimeException("Trying to allocate forge installer file", e);
        }

        boolean success = false;
        // every few major versions Forge would chane their version qualifier scheme :(
        for (final String installerUrlVersion : new String[]{
            String.join("-", minecraftVersion, forgeVersion),
            String.join("-", minecraftVersion, forgeVersion, minecraftVersion)
        }) {
            try {
                fetch(Uris.populateToUri(
                    "https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar",
                    installerUrlVersion, installerUrlVersion
                ))
                    .userAgentCommand("forge")
                    .toFile(installerJar)
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
