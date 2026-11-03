package me.itzg.helpers.files;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErrNormalized;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOutNormalized;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.ExceptionHandler;
import me.itzg.helpers.errors.ExitCodeMapper;
import me.itzg.helpers.sync.MultiCopyCommand;
import me.itzg.helpers.sync.MultiCopyManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class ManifestFilesCommandTest {

    private static final String SCOPE = "var-list";

    @TempDir
    Path tempDir;

    @Test
    void printsSortedFilesFromMcopyManifest() throws Exception {
        final Path srcDir = Files.createDirectories(tempDir.resolve("agents"));
        Files.writeString(srcDir.resolve("zeta-agent.jar"), "z");
        Files.writeString(srcDir.resolve("alpha-agent.jar"), "a");
        Files.writeString(srcDir.resolve("mid-agent.jar"), "m");

        final Path dest = tempDir.resolve("out");
        final int copyExit = new CommandLine(new MultiCopyCommand())
            .execute(
                "--to", dest.toString(),
                "--scope", SCOPE,
                "--glob", "*.jar",
                srcDir.toString()
            );
        assertThat(copyExit).isEqualTo(ExitCode.OK);

        final Path manifest = Manifests.buildManifestPath(dest, SCOPE);
        assertThat(manifest).isRegularFile();

        final MultiCopyManifest loaded = Manifests.load(dest, SCOPE, MultiCopyManifest.class);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getFiles())
            .containsExactlyInAnyOrder("alpha-agent.jar", "mid-agent.jar", "zeta-agent.jar");

        final String sorted = "alpha-agent.jar\nmid-agent.jar\nzeta-agent.jar\n";

        final String fromFile = tapSystemOutNormalized(() ->
            assertThat(execute(manifest.toString())).isEqualTo(ExitCode.OK)
        );
        assertThat(fromFile).isEqualTo(sorted);

        final String fromScope = tapSystemOutNormalized(() ->
            assertThat(execute("--scope", SCOPE, dest.toString())).isEqualTo(ExitCode.OK)
        );
        assertThat(fromScope).isEqualTo(sorted);

        // Rewrite through the same manifest model mcopy uses, in an order that is not sorted,
        // so default output must actually sort and --no-sort must keep manifest order.
        final List<String> manifestOrder = new ArrayList<>(loaded.getFiles());
        manifestOrder.sort(Comparator.reverseOrder());
        Manifests.save(dest, SCOPE, MultiCopyManifest.builder()
            .timestamp(loaded.getTimestamp())
            .files(manifestOrder)
            .build());

        final String sortedAgain = tapSystemOutNormalized(() ->
            assertThat(execute("--sort", manifest.toString())).isEqualTo(ExitCode.OK)
        );
        assertThat(sortedAgain).isEqualTo(sorted);

        final String unsorted = tapSystemOutNormalized(() ->
            assertThat(execute("--no-sort", manifest.toString())).isEqualTo(ExitCode.OK)
        );
        assertThat(unsorted).isEqualTo(String.join("\n", manifestOrder) + "\n");
    }

    @Test
    void emptyFilesListPrintsNothing() throws Exception {
        final Path dest = Files.createDirectories(tempDir.resolve("empty"));
        Manifests.save(dest, SCOPE, MultiCopyManifest.builder()
            .files(List.of())
            .build());

        final Path manifest = Manifests.buildManifestPath(dest, SCOPE);
        final String stdout = tapSystemOutNormalized(() ->
            assertThat(execute(manifest.toString())).isEqualTo(ExitCode.OK)
        );
        assertThat(stdout).isEmpty();

        Manifests.save(dest, "null-files", MultiCopyManifest.builder().build());
        final String nullFiles = tapSystemOutNormalized(() ->
            assertThat(execute(Manifests.buildManifestPath(dest, "null-files").toString()))
                .isEqualTo(ExitCode.OK)
        );
        assertThat(nullFiles).isEmpty();
    }

    @Test
    void missingManifestExitsNonZero() throws Exception {
        final Path missing = tempDir.resolve("missing.json");

        final String err = tapSystemErrNormalized(() ->
            assertThat(execute(missing.toString())).isEqualTo(ExitCode.USAGE)
        );

        assertThat(err).contains("does not exist");

        final Path dest = Files.createDirectories(tempDir.resolve("no-manifest"));
        final String scopeErr = tapSystemErrNormalized(() ->
            assertThat(execute("--scope", SCOPE, dest.toString())).isEqualTo(ExitCode.USAGE)
        );
        assertThat(scopeErr).contains("does not exist");
    }

    @Test
    void invalidManifestExitsNonZero() throws Exception {
        final Path manifest = tempDir.resolve(".var-list-manifest.json");
        Files.writeString(manifest, "{not json");

        final String err = tapSystemErrNormalized(() ->
            assertThat(execute(manifest.toString())).isEqualTo(ExitCode.USAGE)
        );

        assertThat(err).containsIgnoringCase("parse");
    }

    private static int execute(String... args) {
        final String[] command = new String[args.length + 1];
        command[0] = "manifest-files";
        System.arraycopy(args, 0, command, 1, args.length);
        final McImageHelper root = new McImageHelper();
        return new CommandLine(root)
            .setExitCodeExceptionMapper(new ExitCodeMapper())
            .setExecutionExceptionHandler(new ExceptionHandler(root))
            .execute(command);
    }
}
