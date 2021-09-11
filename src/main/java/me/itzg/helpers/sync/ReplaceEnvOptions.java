package me.itzg.helpers.sync;

import lombok.ToString;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

@ToString
public class ReplaceEnvOptions {
    @Option(names = "--replace-env-prefix",
            defaultValue = "CFG_", showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "Only placeholder variables with this prefix will be processed."
    )
    String prefix;

    @Option(names = "--replace-env-excludes", split = ",|\\s", splitSynopsisLabel = ",",
            paramLabel = "FILENAME",
            description = "Filenames (without path) that should be excluded from processing.")
    List<Path> excludes;

    @Option(names = "--replace-env-exclude-paths", split = ",|\\s", splitSynopsisLabel = ",",
            paramLabel = "PATH",
            description = "Destination paths that will be excluded from processing")
    List<Path> excludePaths;

    @Option(names = "--replace-env-file-suffixes", split = ",|\\s", splitSynopsisLabel = ",",
            paramLabel = "PATH",
            required = true,
            description = "Filename suffixes (without dot) that should be processed." +
                    " For example: txt,json,yaml")
    List<String> suffixes;

    /**
     * @param path relative path to a file to evaluate
     * @return true if the file should be processed
     */
    public boolean matches(Path path) {
        if (excludes != null && excludes.stream().anyMatch(other -> path.getFileName().equals(other))) {
            return false;
        }
        if (excludePaths != null && excludePaths.stream().anyMatch(path::startsWith)) {
            return false;
        }
        return suffixes.stream().anyMatch(suffix -> path.getFileName().toString().endsWith("." + suffix));
    }
}
