package me.itzg.helpers.oci;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.oci.OciManifest.OciLayer;
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

    // Allows tests to capture stdout without intercepting the JVM's
    @Setter
    private PrintStream out = System.out;

    // Visible for tests; production code always uses HTTPS
    @Setter
    String scheme = "https";

    public enum FilenameStrategy {
        title,
        digest
    }

    @Override
    public Integer call() throws IOException {
        final OciReference parsedRef = OciReference.parse(ref);
        Files.createDirectories(outputDirectory);

        final RegistryAuthJson auths = RegistryAuthJson.load(authFile);
        try (OciClient client = new OciClient(auths, scheme)) {
            log.info("Resolving OCI artifact {}", parsedRef);
            final OciManifest manifest = client.fetchManifest(parsedRef);
            if (manifest.getLayers() == null || manifest.getLayers().isEmpty()) {
                throw new GenericException(
                    "OCI artifact " + parsedRef + " declares no layers");
            }

            final List<Path> written = new ArrayList<>(manifest.getLayers().size());
            for (final OciLayer layer : manifest.getLayers()) {
                final Path target = outputDirectory.resolve(safeFilename(layer));
                client.downloadBlob(parsedRef, layer.getDigest(), target);
                written.add(target);
            }

            if (layerListFile != null) {
                Files.createDirectories(
                    layerListFile.toAbsolutePath().getParent());
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
                    out.println(p.toAbsolutePath());
                }
            }
        }

        return ExitCode.OK;
    }

    private static final int MAX_FILENAME_LEN = 200;

    private String safeFilename(OciLayer layer) {
        final String candidate;
        if (filenameStrategy == FilenameStrategy.digest) {
            candidate = layer.getDigest();
        } else {
            final String title = layer.title();
            candidate = (title != null && !title.isEmpty()) ? title : layer.getDigest();
        }
        final int lastSep = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
        final String name = lastSep >= 0 ? candidate.substring(lastSep + 1) : candidate;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new InvalidParameterException(
                    "Refusing layer filename containing control characters: " + layer);
            }
        }
        if (name.isEmpty() || name.contains("..") || name.startsWith(".")) {
            throw new InvalidParameterException(
                "Refusing unsafe layer filename: '" + name + "'");
        }
        if (name.length() > MAX_FILENAME_LEN) {
            throw new InvalidParameterException(
                "Layer filename exceeds " + MAX_FILENAME_LEN + " chars: '" + name + "'");
        }
        return name;
    }
}
