package me.itzg.helpers.oci;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import me.itzg.helpers.errors.GenericException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.ExitCode;

@WireMockTest
class InstallOciPackCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void pullsLayersInManifestOrderAndPrintsPaths(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws Exception {
        final byte[] baseLayer = "shared base layer content".getBytes(StandardCharsets.UTF_8);
        final byte[] overlayLayer = "tech overlay content".getBytes(StandardCharsets.UTF_8);
        final String baseDigest = sha256(baseLayer);
        final String overlayDigest = sha256(overlayLayer);

        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/pack";

        stubManifest(repository, "v1",
            buildManifest(baseDigest, baseLayer.length, overlayDigest, overlayLayer.length));
        stubBlob(repository, baseDigest, baseLayer);
        stubBlob(repository, overlayDigest, overlayLayer);

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setInsecure(true);

        final String stdout = tapSystemOutNormalized(() ->
            assertThat(cmd.call()).isEqualTo(ExitCode.OK));

        final Path baseFile = stage.resolve("base.tar.gz");
        final Path overlayFile = stage.resolve("tech.tar.gz");
        assertThat(baseFile).exists().hasBinaryContent(baseLayer);
        assertThat(overlayFile).exists().hasBinaryContent(overlayLayer);

        final String[] printedLines = nonLogLines(stdout);
        assertThat(printedLines).hasSize(2);
        assertThat(Path.of(printedLines[0])).isEqualTo(baseFile.toAbsolutePath());
        assertThat(Path.of(printedLines[1])).isEqualTo(overlayFile.toAbsolutePath());
    }

    @Test
    void writesLayerListFileForScriptedConsumers(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws Exception {
        final byte[] baseLayer = "shared base".getBytes(StandardCharsets.UTF_8);
        final byte[] overlayLayer = "overlay".getBytes(StandardCharsets.UTF_8);
        final String baseDigest = sha256(baseLayer);
        final String overlayDigest = sha256(overlayLayer);

        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/listed";

        stubManifest(repository, "v1",
            buildManifest(baseDigest, baseLayer.length, overlayDigest, overlayLayer.length));
        stubBlob(repository, baseDigest, baseLayer);
        stubBlob(repository, overlayDigest, overlayLayer);

        final Path stage = tempDir.resolve("stage");
        final Path layerList = tempDir.resolve("nested/layers.txt");

        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.layerListFile = layerList;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setInsecure(true);

        final String stdout = tapSystemOutNormalized(() ->
            assertThat(cmd.call()).isEqualTo(ExitCode.OK));

        assertThat(layerList).exists();
        assertThat(Files.readAllLines(layerList))
            .containsExactly(
                stage.resolve("base.tar.gz").toAbsolutePath().toString(),
                stage.resolve("tech.tar.gz").toAbsolutePath().toString()
            );
        // With --layer-list-file set, stdout carries only logger output, no layer paths
        assertThat(nonLogLines(stdout)).isEmpty();
    }

    @Test
    void skipsRedownloadWhenLayerAlreadyOnDisk(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws Exception {
        final byte[] baseLayer = "already cached locally".getBytes(StandardCharsets.UTF_8);
        final String baseDigest = sha256(baseLayer);

        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/cached";

        stubManifest(repository, "v1",
            buildManifest(baseDigest, baseLayer.length, null, 0));
        stubBlob(repository, baseDigest, baseLayer);

        final Path stage = tempDir.resolve("stage");
        Files.createDirectories(stage);
        Files.write(stage.resolve("base.tar.gz"), baseLayer);

        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setInsecure(true);

        tapSystemOutNormalized(() ->
            assertThat(cmd.call()).isEqualTo(ExitCode.OK));

        verify(0, com.github.tomakehurst.wiremock.client.WireMock
            .getRequestedFor(urlPathEqualTo("/v2/" + repository + "/blobs/" + baseDigest)));
    }

    @Test
    void rejectsLayerOnDigestMismatch(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws NoSuchAlgorithmException {
        final byte[] truth = "real layer bytes".getBytes(StandardCharsets.UTF_8);
        final byte[] tampered = "tampered registry response".getBytes(StandardCharsets.UTF_8);
        final String advertisedDigest = sha256(truth);

        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/tampered";

        stubManifest(repository, "v1",
            buildManifest(advertisedDigest, truth.length, null, 0));
        // Server returns different bytes for the advertised digest; the
        // Docker-Content-Digest header echoes the manifest's value so ORAS
        // compares it against the actual payload and rejects the mismatch.
        stubBlob(repository, advertisedDigest, tampered, advertisedDigest);

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setInsecure(true);

        assertThatThrownBy(cmd::call)
            .isInstanceOf(GenericException.class)
            .hasMessageContaining("Digest mismatch");
    }

    // Logback in tests writes to stdout, so strip those lines when asserting on command output
    private static String[] nonLogLines(String stdout) {
        return Arrays.stream(stdout.split("\n"))
            .filter(line -> !line.isEmpty() && !line.startsWith("[mc-image-helper]"))
            .toArray(String[]::new);
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] hash = md.digest(bytes);
        final StringBuilder sb = new StringBuilder("sha256:");
        for (final byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String buildManifest(String baseDigest, int baseSize,
                                        String overlayDigest, int overlaySize) {
        final ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("schemaVersion", 2);
        manifest.put("mediaType", "application/vnd.oci.image.manifest.v1+json");
        manifest.put("artifactType", "application/vnd.itzg.minecraft.modpack.v1+json");
        final ObjectNode config = manifest.putObject("config");
        config.put("mediaType", "application/vnd.oci.empty.v1+json");
        config.put("digest",
            "sha256:44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a");
        config.put("size", 2);

        final ArrayNode layers = manifest.putArray("layers");
        final ObjectNode layer1 = layers.addObject();
        layer1.put("mediaType", "application/vnd.itzg.minecraft.modpack.layer.v1.tar+gzip");
        layer1.put("digest", baseDigest);
        layer1.put("size", baseSize);
        layer1.putObject("annotations")
            .put("org.opencontainers.image.title", "base.tar.gz");

        if (overlayDigest != null) {
            final ObjectNode layer2 = layers.addObject();
            layer2.put("mediaType", "application/vnd.itzg.minecraft.modpack.layer.v1.tar+gzip");
            layer2.put("digest", overlayDigest);
            layer2.put("size", overlaySize);
            layer2.putObject("annotations")
                .put("org.opencontainers.image.title", "tech.tar.gz");
        }

        return manifest.toString();
    }

    private static void stubManifest(String repository, String tag, String body) {
        final String path = "/v2/" + repository + "/manifests/" + tag;
        final ResponseDefinitionBuilder ok = aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/vnd.oci.image.manifest.v1+json")
            .withBody(body);
        // land.oras issues HEAD before GET for manifests
        stubFor(head(urlPathEqualTo(path)).willReturn(ok));
        stubFor(get(urlPathEqualTo(path)).willReturn(ok));
    }

    private static void stubBlob(String repository, String digest, byte[] body) {
        stubBlob(repository, digest, body, null);
    }

    private static void stubBlob(String repository, String digest, byte[] body,
                                 String contentDigest) {
        ResponseDefinitionBuilder b = aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/octet-stream")
            .withBody(body);
        if (contentDigest != null) {
            b = b.withHeader("Docker-Content-Digest", contentDigest);
        }
        stubFor(get(urlPathEqualTo("/v2/" + repository + "/blobs/" + digest)).willReturn(b));
    }
}
