package me.itzg.helpers.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.env.StandardEnvironmentVariablesProvider;
import me.itzg.helpers.patch.model.PatchDefinition;
import me.itzg.helpers.patch.model.PatchSet;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "patch",
    description = "Patches one or more existing files using JSON path based operations%n"
        + "Supports the file formats:%n"
        + "- JSON%n"
        + "- JSON5%n"
        + "- Yaml%n"
        + "- TOML, but processed output is not pretty",
    showDefaultValues = true
)
@Slf4j
public class PatchCommand implements Callable<Integer> {

    private static final String ENV_JSON_ALLOW_COMMENTS = "PATCH_JSON_ALLOW_COMMENTS";
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    boolean showHelp;

    @Option(names = "--patch-env-prefix",
            defaultValue = "CFG_",
            description = "Only placeholder variables with this prefix will be processed"
    )
    String envPrefix;

    @Option(names = "--json-allow-comments", defaultValue = "${env:" + ENV_JSON_ALLOW_COMMENTS + ":-true}",
        description = "Whether to allow comments in JSON files. Env: " + ENV_JSON_ALLOW_COMMENTS,
        showDefaultValue = CommandLine.Help.Visibility.ALWAYS
    )
    boolean jsonAllowComments;

    @Parameters(description = "Path to a PatchSet json file or directory containing PatchDefinition json files",
        paramLabel = "FILE_OR_DIR"
    )
    Path patches;

    private final ObjectMapper patchSetMapper = new ObjectMapper();

    @Override
    public Integer call() throws Exception {
        final PatchSet patchSet;
        try {
            patchSet = loadPatchSet();
        } catch (IOException e) {
            log.error("Failed to load patch definitions from {}: {}", patches, e.getMessage());
            log.debug("Details", e);
            return 2;
        }

        final PatchSetProcessor patchSetProcessor =
                new PatchSetProcessor(
                        new Interpolator(
                                new StandardEnvironmentVariablesProvider(),
                                envPrefix
                        ),
                    jsonAllowComments
                );

        patchSetProcessor.process(patchSet);

        return 0;
    }

    /**
     * Looking at {@link #patches}, loads {@link PatchDefinition}s from a directory or a @{link PatchSet} from a file.
     */
    private PatchSet loadPatchSet() throws IOException {
        if (Files.isDirectory(patches)) {
            final PatchSet patchSet = new PatchSet();
            patchSet.setPatches(new ArrayList<>());

            try (DirectoryStream<Path> dir = Files.newDirectoryStream(patches)) {
                for (Path entry : dir) {
                    // Adds JSON patch definitions from matching files
                    if (Files.isRegularFile(entry)
                            && entry.getFileName().toString().endsWith(".json")) {
                        patchSet.getPatches().add(
                                patchSetMapper.readValue(entry.toFile(), PatchDefinition.class)
                                    .setSrc(entry)
                        );
                    }
                }
            }

            return patchSet;
        }
        else {
            return setSource(patches, patchSetMapper.readValue(patches.toFile(), PatchSet.class));
        }
    }

    private PatchSet setSource(Path src, PatchSet patchSet) {
        patchSet.getPatches().forEach(patch -> patch.setSrc(src));
        return patchSet;
    }
}
