package me.itzg.helpers.vanillatweaks;

import static me.itzg.helpers.McImageHelper.OPTION_SPLIT_COMMAS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
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
import me.itzg.helpers.Manifests;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.HttpClientException;
import me.itzg.helpers.http.ReactorNettyBits;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.vanillatweaks.model.PackDefinition;
import me.itzg.helpers.vanillatweaks.model.Type;
import me.itzg.helpers.vanillatweaks.model.ZipLinkResponse;
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

    public static final String MANIFEST_FILENAME = ".vanillatweaks.manifest";
    private static final int FINGERPRINT_LENGTH = 7;

    @SuppressWarnings("unused") // used by picocli
    public VanillaTweaksCommand() {
        this("https://vanillatweaks.net");
    }

    public VanillaTweaksCommand(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Option(names = "--share-codes", required = true, split = OPTION_SPLIT_COMMAS, paramLabel = "CODE")
    List<String> shareCodes;

    @Option(names = "--pack-files", split = OPTION_SPLIT_COMMAS, paramLabel = "FILE")
    List<Path> packFiles;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--world-subdir", defaultValue = "world")
    String worldSubdir;

    private static final ReactorNettyBits bits = new ReactorNettyBits();

    private static final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

    private final Set<Path> writtenFiles = Collections.synchronizedSet(new HashSet<>());
    private final String baseUrl;
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

        final Path manifestPath = outputDirectory.resolve(MANIFEST_FILENAME);

        worldPath = outputDirectory.resolve(worldSubdir);
        Files.createDirectories(worldPath);

        final Manifest oldManifest;
        if (Files.exists(manifestPath)) {
            oldManifest = objectMapper.readValue(manifestPath.toFile(), Manifest.class);
            log.debug("Loaded existing manifest={}", oldManifest);
        } else {
            oldManifest = null;
        }

        loadPackDefinitions()
            .concatWith(resolveShareCodes())
            .parallel()
            .runOn(Schedulers.parallel())
            .flatMap(packDefinition -> processPackDefinition(packDefinition.source, packDefinition.packDefinition))
            .then()
            .block();

        final Manifest newManifest = Manifest.builder()
            .timestamp(Instant.now())
            .shareCodes(shareCodes)
            .files(
                writtenFiles.stream()
                    .map(path -> outputDirectory.relativize(path).toString())
                    .collect(Collectors.toSet())
            )
            .build();

        if (oldManifest != null) {
            Manifests.cleanup(outputDirectory, oldManifest.getFiles(), newManifest.getFiles(),
                file -> log.debug("Deleting old file={}", file)
            );
        }

        objectMapper.writerWithDefaultPrettyPrinter()
            .writeValue(manifestPath.toFile(), newManifest);

        return ExitCode.OK;
    }

    private Flux<SourcedPackDefinition> resolveShareCodes() {
        if (shareCodes == null) {
            return Flux.empty();
        }

        return Flux.fromIterable(shareCodes)
            // handle --arg= case
            .filter(shareCode -> !shareCode.isEmpty())
            .flatMap(this::resolveShareCode);
    }


    private Flux<SourcedPackDefinition> loadPackDefinitions() {
        if (packFiles == null) {
            return Flux.empty();
        }

        return Flux.fromIterable(packFiles)
            // handle --arg= case
            .filter(path -> !path.toString().isEmpty())
            .map(path -> {
                try {
                    return new SourcedPackDefinition(
                        "definition file " + path,
                        objectMapper.readValue(path.toFile(), PackDefinition.class)
                    );
                } catch (IOException e) {
                    throw new GenericException("Failed to load pack definition file", e);
                }
            });
    }

    private Mono<Void> processPackDefinition(String source, PackDefinition packDefinition) {
        final String fingerprint = calculateFingerprint(packDefinition);
        return resolveDownloadLink(packDefinition)
            .flatMap(url -> downloadVanillaTweaksZip(source, packDefinition.getType(), url))
            .publishOn(Schedulers.boundedElastic())
            .flatMap(zipPath -> {
                try {
                    unpack(packDefinition.getType(), zipPath, fingerprint);
                    return Mono.empty();
                } catch (IOException e) {
                    return Mono.error(e);
                }
            });
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

    private void unpack(Type type, Path zipPath, String fingerprint) throws IOException {
        switch (type) {
            case resourcepacks: {
                final Path resourcepacks = outputDirectory.resolve("resourcepacks");
                Files.createDirectories(resourcepacks);

                writtenFiles.add(
                    Files.move(zipPath,
                        resourcepacks.resolve(String.format("VanillaTweaks_%s.zip", fingerprint)),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                );
                break;
            }

            case datapacks: {
                final Path datapacks = worldPath.resolve("datapacks");
                Files.createDirectories(datapacks);
                unzipInto(zipPath, datapacks);
                Files.delete(zipPath);
                break;
            }

            case craftingtweaks: {
                final Path datapacks = worldPath.resolve("datapacks");
                Files.createDirectories(datapacks);

                writtenFiles.add(
                    Files.move(zipPath,
                        datapacks.resolve(String.format("VanillaTweaks_%s.zip", fingerprint)),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                );
                break;
            }
        }
    }

    private void unzipInto(Path zipPath, Path datapacks) throws IOException {
        try (InputStream in = Files.newInputStream(zipPath);
            ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final Path target = datapacks.resolve(entry.getName());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                writtenFiles.add(target);
            }
        }
    }

    private Mono<Path> downloadVanillaTweaksZip(String source, Type type, String url) {
        log.info("Downloading Vanilla Tweaks {} for {}", type, source);
        return bits.client()
            .headers(entries -> entries.add(HttpHeaderNames.ACCEPT, "application/zip"))
            .get()
            .uri(url)
            .responseContent().aggregate()
            .asInputStream()
            .publishOn(Schedulers.boundedElastic())
            .map(inputStream -> {
                try {
                    final Path downloadedZip = Files.createTempFile("vanillatweaks", ".zip");
                    Files.copy(inputStream, downloadedZip, StandardCopyOption.REPLACE_EXISTING);
                    return downloadedZip;
                } catch (IOException e) {
                    throw new GenericException("Trying to write downloaded pack zip file", e);
                }
            });
    }

    private Mono<String> resolveDownloadLink(PackDefinition packDefinition) {
        return bits.jsonClient()
            .post()
            .uri(
                Uris.populate(
                    baseUrl + "/assets/server/zip{type}.php",
                    packDefinition.getType().toString()
                )
            )
            .sendForm((httpClientRequest, httpClientForm) -> {
                try {
                    httpClientForm
                        .attr("packs", objectMapper.writeValueAsString(packDefinition.getPacks()))
                        .attr("version", packDefinition.getVersion());
                } catch (JsonProcessingException e) {
                    throw new GenericException("Failed to encode packs", e);
                }
            })
            .responseSingle(bits.readInto(ZipLinkResponse.class))
            .map(zipLinkResponse -> baseUrl + zipLinkResponse.getLink());
    }

    private Mono<SourcedPackDefinition> resolveShareCode(String shareCode) {
        return bits.jsonClient()
            .get()
            .uri(
                Uris.populate(baseUrl + "/assets/server/sharecode.php?code={code}", shareCode)
            )
            .responseSingle(bits.readInto(PackDefinition.class))
            .onErrorResume(HttpClientException::isNotFound,
                throwable -> Mono.error(new InvalidParameterException("Unable to resolve share code " + shareCode, throwable))
            )
            .map(packDefinition -> new SourcedPackDefinition(
                    "share code " + shareCode, packDefinition
                )
            );
    }
}
