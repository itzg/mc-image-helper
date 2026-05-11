package me.itzg.helpers.oci;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine.ExitCode;

@WireMockTest
class InstallOciPackCommandTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void pullsLayersInManifestOrderAndPrintsPaths(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, NoSuchAlgorithmException {
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
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8.name()));
        cmd.setScheme("http");

        final Integer exit = cmd.call();

        assertThat(exit).isEqualTo(ExitCode.OK);

        final Path baseFile = stage.resolve("base.tar.gz");
        final Path overlayFile = stage.resolve("tech.tar.gz");
        assertThat(baseFile).exists().hasBinaryContent(baseLayer);
        assertThat(overlayFile).exists().hasBinaryContent(overlayLayer);

        final String[] printedLines = stdout.toString(StandardCharsets.UTF_8.name()).split("\\R");
        assertThat(printedLines).hasSize(2);
        assertThat(Path.of(printedLines[0])).isEqualTo(baseFile.toAbsolutePath());
        assertThat(Path.of(printedLines[1])).isEqualTo(overlayFile.toAbsolutePath());
    }

    @Test
    void writesLayerListFileForScriptedConsumers(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, NoSuchAlgorithmException {
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
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.layerListFile = layerList;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8.name()));
        cmd.setScheme("http");

        assertThat(cmd.call()).isEqualTo(ExitCode.OK);

        assertThat(layerList).exists();
        assertThat(Files.readAllLines(layerList))
            .containsExactly(
                stage.resolve("base.tar.gz").toAbsolutePath().toString(),
                stage.resolve("tech.tar.gz").toAbsolutePath().toString()
            );
        assertThat(stdout.toString(StandardCharsets.UTF_8.name())).isEmpty();
    }

    @Test
    void skipsRedownloadWhenLayerAlreadyOnDisk(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, NoSuchAlgorithmException {
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
        cmd.setOut(new PrintStream(new ByteArrayOutputStream()));
        cmd.setScheme("http");

        assertThat(cmd.call()).isEqualTo(ExitCode.OK);

        verify(0, com.github.tomakehurst.wiremock.client.WireMock
            .getRequestedFor(urlPathEqualTo("/v2/" + repository + "/blobs/" + baseDigest)));
    }

    @Test
    void rejectsImageIndexByContentType(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) {
        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/multiarch";

        stubFor(get(urlPathEqualTo("/v2/" + repository + "/manifests/v1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/vnd.oci.image.index.v1+json")
                .withBody(buildImageIndex())));

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(new ByteArrayOutputStream()));
        cmd.setScheme("http");

        assertThatThrownBy(cmd::call)
            .isInstanceOf(GenericException.class)
            .hasMessageContaining("image index");
    }

    @Test
    void rejectsImageIndexByJsonBodyWhenContentTypeIsMisleading(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) {
        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/multiarch-mislabelled";

        stubFor(get(urlPathEqualTo("/v2/" + repository + "/manifests/v1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(buildImageIndex())));

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(new ByteArrayOutputStream()));
        cmd.setScheme("http");

        assertThatThrownBy(cmd::call)
            .isInstanceOf(GenericException.class)
            .hasMessageContaining("image index");
    }

    @Test
    void rejectsLayerWithControlCharsInTitle(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, NoSuchAlgorithmException {
        final byte[] layer = "x".getBytes(StandardCharsets.UTF_8);
        final String digest = sha256(layer);

        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/nasty-title";

        final ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("schemaVersion", 2);
        manifest.put("mediaType", "application/vnd.oci.image.manifest.v1+json");
        final ArrayNode layers = manifest.putArray("layers");
        final ObjectNode l = layers.addObject();
        l.put("mediaType", "application/octet-stream");
        l.put("digest", digest);
        l.put("size", layer.length);
        final String unsafeTitle = "evil" + (char) 0x09 + "name.tar.gz";
        l.putObject("annotations")
            .put("org.opencontainers.image.title", unsafeTitle);

        stubManifest(repository, "v1", manifest.toString());
        stubBlob(repository, digest, layer);

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(new ByteArrayOutputStream()));
        cmd.setScheme("http");

        assertThatThrownBy(cmd::call)
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("control characters");
    }

    @Test
    void completesBearerChallengeFlow(
        WireMockRuntimeInfo wm, @TempDir Path tempDir
    ) throws IOException, NoSuchAlgorithmException {
        final byte[] layer = "bearer-protected layer".getBytes(StandardCharsets.UTF_8);
        final String digest = sha256(layer);

        final URI base = URI.create(wm.getHttpBaseUrl());
        final String registry = base.getHost() + ":" + base.getPort();
        final String repository = "owner/private";
        final String tokenPath = "/auth/token";
        final String tokenValue = "fake-bearer-token-12345";
        final String bearerHeader = "Bearer " + tokenValue;

        stubFor(get(urlPathEqualTo("/v2/" + repository + "/manifests/v1"))
            .withHeader("Authorization", absent())
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("WWW-Authenticate",
                    "Bearer realm=\"" + wm.getHttpBaseUrl() + tokenPath + "\","
                        + "service=\"test-registry\","
                        + "scope=\"repository:" + repository + ":pull\"")));

        final ObjectNode tokenBody = MAPPER.createObjectNode();
        tokenBody.put("token", tokenValue);
        stubFor(get(urlPathEqualTo(tokenPath))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(tokenBody.toString())));

        stubFor(get(urlPathEqualTo("/v2/" + repository + "/manifests/v1"))
            .withHeader("Authorization", equalTo(bearerHeader))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/vnd.oci.image.manifest.v1+json")
                .withBody(buildManifest(digest, layer.length, null, 0))));

        stubFor(get(urlPathEqualTo("/v2/" + repository + "/blobs/" + digest))
            .withHeader("Authorization", equalTo(bearerHeader))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withBody(layer)));

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(new ByteArrayOutputStream()));
        cmd.setScheme("http");

        assertThat(cmd.call()).isEqualTo(ExitCode.OK);
        assertThat(stage.resolve("base.tar.gz")).exists().hasBinaryContent(layer);

        verify(1, getRequestedFor(urlPathEqualTo(tokenPath)));
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
        stubBlob(repository, advertisedDigest, tampered);

        final Path stage = tempDir.resolve("stage");
        final InstallOciPackCommand cmd = new InstallOciPackCommand();
        cmd.ref = registry + "/" + repository + ":v1";
        cmd.outputDirectory = stage;
        cmd.filenameStrategy = InstallOciPackCommand.FilenameStrategy.title;
        cmd.setOut(new PrintStream(new ByteArrayOutputStream()));
        cmd.setScheme("http");

        assertThatThrownBy(cmd::call)
            .isInstanceOf(GenericException.class)
            .hasMessageContaining("Digest mismatch");
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

    private static String buildImageIndex() {
        final ObjectNode index = MAPPER.createObjectNode();
        index.put("schemaVersion", 2);
        index.put("mediaType", "application/vnd.oci.image.index.v1+json");
        final ArrayNode manifests = index.putArray("manifests");
        final ObjectNode m = manifests.addObject();
        m.put("mediaType", "application/vnd.oci.image.manifest.v1+json");
        m.put("digest",
            "sha256:0000000000000000000000000000000000000000000000000000000000000000");
        m.put("size", 123);
        m.putObject("platform")
            .put("architecture", "amd64")
            .put("os", "linux");
        return index.toString();
    }

    private static void stubManifest(String repository, String tag, String body) {
        stubFor(get(urlPathEqualTo("/v2/" + repository + "/manifests/" + tag))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/vnd.oci.image.manifest.v1+json")
                .withBody(body)));
    }

    private static void stubBlob(String repository, String digest, byte[] body) {
        stubFor(get(urlPathEqualTo("/v2/" + repository + "/blobs/" + digest))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withBody(body)));
    }
}
