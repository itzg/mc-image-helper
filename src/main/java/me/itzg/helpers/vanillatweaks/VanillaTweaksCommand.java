package me.itzg.helpers.vanillatweaks;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.singles.MoreCollections;
import me.itzg.helpers.vanillatweaks.model.PackDefinition;
import me.itzg.helpers.vanillatweaks.model.Type;
import me.itzg.helpers.vanillatweaks.model.ZipLinkResponse;
import org.jetbrains.annotations.Blocking;
import org.reactivestreams.Publisher;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Command(name = "vanillatweaks", description = "Downloads Vanilla Tweaks resource packs, data packs, or crafting tweaks"
    + " given a share code or pack file")
@Slf4j
public class VanillaTweaksCommand implements Callable<Integer> {

    public static final String LEGACY_MANIFEST_FILENAME = ".vanillatweaks.manifest";
    public static final String MANIFEST_ID = "vanillatweaks";
    private static final int FINGERPRINT_LENGTH = 7;

    @Option(names = "--share-codes", split = SPLIT_COMMA_NL, splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "CODE"
    )
    List<String> shareCodes;

    @Option(names = "--pack-files", split = SPLIT_COMMA_NL, splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "FILE"
    )
    List<Path> packFiles;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--world-subdir", defaultValue = "world")
    String worldSubdir;

    @Option(names = "--base-url", defaultValue = "${env:VT_BASE_URL:-https://vanillatweaks.net}")
    String baseUrl;

    @Option(names = "--force-synchronize", defaultValue = "${env:VT_FORCE_SYNCHRONIZE:-false}")
    boolean forceSynchronize;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    private static final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

    private final Set<Path> writtenFiles = Collections.synchronizedSet(new HashSet<>());
    private Path worldPath;

    @AllArgsConstructor
    static class SourcedPackDefinition {

        String source;
        PackDefinition packDefinition;
    }

    @Override
    public Integer call() throws Exception {
        if (shareCodes.isEmpty() && packFiles.isEmpty()) {
            System.err.println("Either share codes or pack files needed to be given");
            return ExitCode.USAGE;
        }

        final VanillaTweaksManifest oldManifest = loadManifest();

        if (oldManifest != null &&
            sameInputs(oldManifest) &&
            Manifests.allFilesPresent(outputDirectory, oldManifest)
        ) {
            if (!forceSynchronize) {
                log.info("Requested VanillaTweaks content is already present");
                return ExitCode.OK;
            }
        }

        worldPath = outputDirectory.resolve(worldSubdir);
        Files.createDirectories(worldPath);

        try (SharedFetch sharedFetch = Fetch.sharedFetch("vanillatweaks", sharedFetchArgs.options())) {
            loadPackDefinitions()
                .concatWith(resolveShareCodes(sharedFetch))
                .flatMap(packDefinition -> processPackDefinition(sharedFetch, packDefinition.source,
                    packDefinition.packDefinition
                ))
                .then()
                .block();

        }


        final VanillaTweaksManifest newManifest = VanillaTweaksManifest.builder()
            .shareCodes(shareCodes)
            .packFiles(packFiles != null ?
                packFiles.stream()
                    .map(Path::toString)
                    .collect(Collectors.toList())
                : null
            )
            .files(Manifests.relativizeAll(outputDirectory, writtenFiles))
            .build();

        Manifests.cleanup(outputDirectory, oldManifest, newManifest, log);
        Manifests.save(outputDirectory, MANIFEST_ID, newManifest);

        return ExitCode.OK;
    }

    private boolean sameInputs(VanillaTweaksManifest oldManifest) {
        if (packFiles != null && !packFiles.isEmpty()) {
            // for now, presence of pack files, rather than share codes defeats same-ness
            return false;
        }
        return MoreCollections.equalsIgnoreOrder(
            oldManifest.getShareCodes(),
            shareCodes
        );
    }

    private VanillaTweaksManifest loadManifest() throws IOException {
        final Path legacyManifestPath = outputDirectory.resolve(LEGACY_MANIFEST_FILENAME);

        if (Files.exists(legacyManifestPath)) {
            final LegacyManifest oldManifest;
            oldManifest = objectMapper.readValue(legacyManifestPath.toFile(), LegacyManifest.class);
            Files.delete(legacyManifestPath);
            log.debug("Loaded legacy manifest={}", oldManifest);
            return VanillaTweaksManifest.builder()
                .shareCodes(oldManifest.getShareCodes())
                .files(oldManifest.getFiles())
                .build();
        } else {
            return Manifests.load(outputDirectory, MANIFEST_ID, VanillaTweaksManifest.class);
        }
    }

    private Flux<SourcedPackDefinition> resolveShareCodes(SharedFetch sharedFetch) {
        if (shareCodes == null) {
            return Flux.empty();
        }

        return Flux.fromStream(
                shareCodes.stream()
                    // handle --arg= case
                    .filter(s -> !s.isEmpty())
            )
            .flatMap(shareCode -> resolveShareCode(sharedFetch, shareCode));
    }


    private Flux<SourcedPackDefinition> loadPackDefinitions() {
        if (packFiles == null) {
            return Flux.empty();
        }

        return Flux.fromStream(
                packFiles.stream()
                    // handle --arg= case
                    .filter(path -> !path.toString().isEmpty())
            )
            .flatMap(path -> {
                try {
                    final PackDefinition packDefinition = objectMapper.readValue(path.toFile(), PackDefinition.class);
                    if (packDefinition.getType() == null) {
                        return Mono.error(
                            new InvalidParameterException(String.format("Pack definition file %s is missing 'type'", path)));
                    }
                    else {
                        return Mono.just(new SourcedPackDefinition(
                            "definition file " + path,
                            packDefinition
                        ));
                    }
                } catch (IOException e) {
                    return Mono.error(new GenericException("Failed to load pack definition file", e));
                }
            });
    }

    private Mono<Void> processPackDefinition(SharedFetch sharedFetch, String source, PackDefinition packDefinition) {
        return resolveDownloadLink(sharedFetch, packDefinition)
            .flatMapMany(url -> {
                try {
                    return downloadVanillaTweaksZip(
                        sharedFetch, source, packDefinition.getType(), url, calculateFingerprint(packDefinition));
                } catch (IOException e) {
                    return Flux.error(e);
                }
            })
            .doOnNext(writtenFiles::add)
            .then();
    }

    private String calculateFingerprint(PackDefinition packDefinition) {
        try {
            final MessageDigest digester = MessageDigest.getInstance("SHA-1");
            digester.update(packDefinition.getType().toString().getBytes(StandardCharsets.UTF_8));
            digester.update(packDefinition.getVersion().getBytes(StandardCharsets.UTF_8));

            // sort categories to fingerprint consistently
            final List<String> categories = packDefinition.getPacks().keySet().stream()
                .sorted()
                .collect(Collectors.toList());
            for (final String category : categories) {
                digester.update(category.getBytes(StandardCharsets.UTF_8));
                // sort packs to fingerprint consistently
                final List<String> packs = packDefinition.getPacks().get(category).stream()
                    .sorted()
                    .collect(Collectors.toList());
                for (final String pack : packs) {
                    digester.update(pack.getBytes(StandardCharsets.UTF_8));
                }
            }

            final byte[] digest = digester.digest();
            final String fingerprint = new BigInteger(1, digest).toString(16);

            return fingerprint.substring(0, FINGERPRINT_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new GenericException("Failed to get digester for fingerprinting", e);
        }
    }

    private Path resourcePacksDir() throws IOException {
        return Files.createDirectories(outputDirectory.resolve("resourcepacks"));
    }

    private Path dataPacksDir() throws IOException {
        return Files.createDirectories(worldPath.resolve("datapacks"));
    }

    @SuppressWarnings("BlockingMethodInNonBlockingContext")
    @Blocking
    private Flux<Path> unzipInto(Path zipPath, Path outDir) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (InputStream in = Files.newInputStream(zipPath);
             ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path target = outDir.resolve(entry.getName());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                result.add(target);
            }
        }
        return Flux.fromIterable(result);
    }

    private Publisher<Path> downloadVanillaTweaksZip(SharedFetch sharedFetch, String source, Type type, String url, String packId) throws IOException {
        log.info("Downloading Vanilla Tweaks {} for {}", type, source);

        switch (type) {
            case resourcepacks:
                return sharedFetch
                    .fetch(URI.create(url))
                    .toFile(resourcePacksDir().resolve(String.format("VanillaTweaks_%s.zip", packId)))
                    .skipExisting(true)
                    .assemble()
                    .checkpoint("Downloading resource pack zip");

            case craftingtweaks:
                return sharedFetch
                    .fetch(URI.create(url))
                    .toFile(dataPacksDir().resolve(String.format("VanillaTweaks_%s.zip", packId)))
                    .skipExisting(true)
                    .assemble()
                    .checkpoint("Downloading crafting tweaks zip");

            case datapacks:
                return Mono.just("")
                    .publishOn(Schedulers.boundedElastic())
                    .flatMapMany(s -> {
                        final Path tempZip;
                        try {
                            //noinspection BlockingMethodInNonBlockingContext
                            tempZip = Files.createTempFile("VT", ".zip");
                        } catch (IOException e) {
                            return Flux.error(new GenericException("Failed to create temp zip for datapack", e));
                        }
                        return sharedFetch
                            .fetch(URI.create(url))
                            .toFile(tempZip)
                            .assemble()
                            .checkpoint("Downloading datapack zip")
                            .publishOn(Schedulers.boundedElastic())
                            .flatMapMany(downloaded -> {
                                try {
                                    //noinspection BlockingMethodInNonBlockingContext because IntelliJ is confused
                                    return unzipInto(tempZip, dataPacksDir());
                                } catch (IOException e) {
                                    return Flux.error(e);
                                }
                            })
                            .doOnTerminate(() -> {
                                try {
                                    Files.delete(tempZip);
                                } catch (IOException e) {
                                    throw new GenericException("Failed to delete temp datapack zip", e);
                                }
                            });
                    });

            default:
                return Flux.error(new GenericException("Unexpected type: " + type));
        }
    }

    private Mono<String> resolveDownloadLink(SharedFetch sharedFetch, PackDefinition packDefinition) {
        return sharedFetch
            .fetch(
                Uris.populateToUri(
                    baseUrl + "/assets/server/zip{type}.php",
                    packDefinition.getType().toString()
                )
            )
            .sendForm(httpClientForm -> {
                    try {
                        httpClientForm
                            .attr("packs", objectMapper.writeValueAsString(packDefinition.getPacks()))
                            .attr("version", packDefinition.getVersion());
                    } catch (JsonProcessingException e) {
                        throw new GenericException("Failed to encode packs", e);
                    }
                }
            )
            .toObject(ZipLinkResponse.class)
            .acceptContentTypes(null)
            .assemble()
            .checkpoint("Resolving download link")
            .map(zipLinkResponse -> baseUrl + zipLinkResponse.getLink());
    }

    private Mono<SourcedPackDefinition> resolveShareCode(SharedFetch sharedFetch, String shareCode) {
        return sharedFetch
            .fetch(Uris.populateToUri(baseUrl + "/assets/server/sharecode.php?code={code}", shareCode))
            .toObject(PackDefinition.class)
            .acceptContentTypes(null)
            .assemble()
            .checkpoint("Resolving share code")
            .onErrorMap(
                FailedRequestException::isNotFound,
                throwable -> new InvalidParameterException("Unable to resolve share code " + shareCode, throwable)
            )
            .map(packDefinition -> new SourcedPackDefinition(
                    "share code " + shareCode, packDefinition
                )
            );
    }
}
