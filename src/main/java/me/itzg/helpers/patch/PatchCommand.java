package me.itzg.helpers.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.env.StandardEnvironmentVariablesProvider;
import me.itzg.helpers.patch.model.PatchDefinition;
import me.itzg.helpers.patch.model.PatchSet;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "patch",
    description = "Patches one or more existing files using JSON path based operations"
)
@Slf4j
public class PatchCommand implements Callable<Integer> {
    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    boolean showHelp;

    @CommandLine.Option(names = "--patch-env-prefix",
            defaultValue = "CFG_",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "Only placeholder variables with this prefix will be processed"
    )
    String envPrefix;

    @CommandLine.Parameters(description = "Path to a PatchSet json file or directory containing PatchDefinition json files",
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
                        )
                );

        patchSetProcessor.process(patchSet);

        return 0;
    }

    private PatchSet loadPatchSet() throws IOException {
        if (Files.isDirectory(patches)) {
            final PatchSet patchSet = new PatchSet();
            patchSet.setPatches(new ArrayList<>());

            try (DirectoryStream<Path> dir = Files.newDirectoryStream(patches)) {
                for (Path entry : dir) {
                    if (Files.isRegularFile(entry)
                            && entry.getFileName().toString().endsWith(".json")) {
                        patchSet.getPatches().add(
                                patchSetMapper.readValue(entry.toFile(), PatchDefinition.class)
                        );
                    }
                }
            }

            return patchSet;
        }
        else {
            return patchSetMapper.readValue(patches.toFile(), PatchSet.class);
        }
    }
}
