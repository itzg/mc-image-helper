package me.itzg.helpers.oci;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import land.oras.ContainerRef;
import land.oras.Layer;
import land.oras.Manifest;
import land.oras.Registry;
import land.oras.auth.AuthProvider;
import land.oras.auth.AuthStore;
import land.oras.auth.AuthStoreAuthenticationProvider;
import land.oras.auth.NoAuthProvider;
import land.oras.exception.OrasException;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "install-oci-pack",
    description = "Pulls an OCI artifact and writes its layer blobs to disk in apply order",
    mixinStandardHelpOptions = true)
@Slf4j
public class InstallOciPackCommand implements Callable<Integer> {

    @Option(names = "--ref", required = true, paramLabel = "REF",
        description = "OCI reference, e.g. ghcr.io/owner/pack:v1 or ghcr.io/owner/pack@sha256:..."
            + "%nThe optional oci:// prefix is tolerated.")
    String ref;

    @Option(names = "--output-directory", required = true, paramLabel = "DIR",
        description = "Directory where layer blobs are written. Acts as a content-addressed"
            + " cache between invocations: layers whose digest already exists are not re-downloaded.")
    Path outputDirectory;

    @Option(names = "--auth-file", paramLabel = "FILE",
        description = "Registry login JSON (root auths map). When unset, reads the default"
            + " login file under the user home directory if present.")
    Path authFile;

    @Option(names = "--filename-strategy", defaultValue = "title",
        description = "How to name layer files on disk. Valid values: ${COMPLETION-CANDIDATES}."
            + "%nDefault: ${DEFAULT-VALUE}")
    FilenameStrategy filenameStrategy;

    @Option(names = "--layer-list-file", paramLabel = "FILE",
        description = "Write each pulled layer's absolute path on its own line to this file,"
            + " in manifest layer order. Suitable for `mapfile -t ... < FILE` in shell."
            + " When omitted, layer paths are also printed to stdout for interactive use.")
    Path layerListFile;

    // Visible for tests so they can point at a plain-HTTP WireMock registry
    @Setter(AccessLevel.PACKAGE)
    boolean insecure;

    public enum FilenameStrategy {
        /**
         * Use the layer's {@code org.opencontainers.image.title} annotation,
         * falling back to its digest when the annotation is absent.
         */
        title,
        /** Always use the layer's digest as the filename. */
        digest
    }

    @Override
    public Integer call() throws IOException {
        final ContainerRef cref;
        try {
            cref = ContainerRef.parse(ref);
        } catch (IllegalArgumentException e) {
            throw new InvalidParameterException(
                "Invalid OCI reference: " + ref + ": " + e.getMessage(), e);
        }

        Files.createDirectories(outputDirectory);
        final Registry registry = buildRegistry();
        log.info("Resolving OCI artifact {}", cref);
        try {
            final Manifest manifest = registry.getManifest(cref);
            if (manifest.getLayers() == null || manifest.getLayers().isEmpty()) {
                throw new GenericException("OCI artifact " + cref + " declares no layers");
            }

            final List<Path> written = new ArrayList<>(manifest.getLayers().size());
            for (final Layer layer : manifest.getLayers()) {
                final Path target = outputDirectory.resolve(filenameFor(layer));
                final String digest = layer.getDigest();
                if (digest == null) {
                    throw new GenericException(
                        "OCI artifact " + cref + " has a layer without a digest");
                }
                if (Files.isRegularFile(target) && verifyExisting(target, digest)) {
                    log.debug("Layer {} already on disk at {}; skipping download", digest, target);
                } else {
                    try {
                        registry.fetchBlob(cref.withDigest(digest), target);
                    } catch (OrasException e) {
                        throw new GenericException(
                            "Failed to download OCI layer " + digest + ": " + e.getMessage(), e);
                    }
                    verifyDownloaded(target, digest);
                }
                written.add(target);
            }

            if (layerListFile != null) {
                Files.createDirectories(layerListFile.toAbsolutePath().getParent());
                try (BufferedWriter w = Files.newBufferedWriter(
                    layerListFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (final Path p : written) {
                        w.write(p.toAbsolutePath().toString());
                        w.newLine();
                    }
                }
            } else {
                for (final Path p : written) {
                    System.out.println(p.toAbsolutePath());
                }
            }
        } catch (OrasException e) {
            throw new GenericException(
                "Failed to pull OCI artifact " + cref + ": " + e.getMessage(), e);
        }

        return ExitCode.OK;
    }

    private Registry buildRegistry() {
        final Registry.Builder b = Registry.builder()
            .withAuthProvider(resolveAuthProvider());
        if (insecure) {
            b.insecure();
        }
        return b.build();
    }

    // ORAS AuthStore.newStore() throws on empty or malformed login files
    // (e.g. missing 'auths'); tests and minimal images often have no usable
    // file, so fall back to anonymous pulls.
    private AuthProvider resolveAuthProvider() {
        Path config = null;
        if (authFile != null && Files.isReadable(authFile)) {
            config = authFile;
        } else {
            final String home = System.getProperty("user.home");
            if (home != null) {
                final Path containersAuth = Path.of(home, ".config", "containers", "auth.json");
                if (Files.isReadable(containersAuth)) {
                    config = containersAuth;
                } else {
                    final Path def = Path.of(home, ".docker", "config.json");
                    if (Files.isReadable(def)) {
                        config = def;
                    }
                }
            }
        }
        if (config == null) {
            return new NoAuthProvider();
        }
        try {
            return new AuthStoreAuthenticationProvider(
                AuthStore.newStore(Collections.singletonList(config)));
        } catch (RuntimeException e) {
            log.debug("Registry login file at {} not usable by ORAS ({}); using no auth",
                config, e.getMessage());
            return new NoAuthProvider();
        }
    }

    private String filenameFor(Layer layer) {
        final String candidate;
        if (filenameStrategy == FilenameStrategy.digest) {
            candidate = layer.getDigest();
        } else {
            final String title = layerTitle(layer);
            candidate = title != null && !title.isBlank() ? title : layer.getDigest();
        }
        final Path p = Path.of(candidate).getFileName();
        if (p == null) {
            throw new InvalidParameterException(
                "Could not derive a filename from layer: " + layer);
        }
        return p.toString();
    }

    private static String layerTitle(Layer layer) {
        final Map<String, String> annotations = layer.getAnnotations();
        if (annotations == null) {
            return null;
        }
        return annotations.get("org.opencontainers.image.title");
    }

    private static boolean verifyExisting(Path target, String expectedDigest) {
        if (!"sha256:".regionMatches(true, 0, expectedDigest, 0, "sha256:".length())) {
            return false;
        }
        try {
            return computeSha256(target).equalsIgnoreCase(expectedDigest);
        } catch (IOException e) {
            log.debug("Could not verify existing layer at {}: {}", target, e.getMessage());
            return false;
        }
    }

    // The ORAS SDK only verifies blob bytes against the Docker-Content-Digest
    // response header, and silently skips verification when that header is
    // absent (the OCI distribution spec marks it SHOULD, not MUST). Recompute
    // the digest ourselves against the manifest's declared value so a
    // misbehaving registry or stripping proxy cannot serve us tampered bytes.
    private static void verifyDownloaded(Path target, String expectedDigest) {
        if (!"sha256:".regionMatches(true, 0, expectedDigest, 0, "sha256:".length())) {
            return;
        }
        final String actual;
        try {
            actual = computeSha256(target);
        } catch (IOException e) {
            throw new GenericException(
                "Failed to verify digest of " + target + ": " + e.getMessage(), e);
        }
        if (!actual.equalsIgnoreCase(expectedDigest)) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
                // best-effort cleanup
            }
            throw new GenericException(String.format(
                "Digest mismatch for OCI blob: expected %s, got %s",
                expectedDigest, actual));
        }
    }

    private static String computeSha256(Path target) throws IOException {
        final MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable in this JRE", e);
        }
        try (InputStream in = Files.newInputStream(target)) {
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                sha.update(buf, 0, n);
            }
        }
        return "sha256:" + toHex(sha.digest());
    }

    private static String toHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
