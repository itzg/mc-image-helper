package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;

import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "archive", description = "Archive helpers, checking for path traversal and extracting archives")
public class ArchiveCommand {
    @Option(names = { "--help", "-h" }, usageHelp = true)
    boolean help;

    @Command(name = "check-path-traversal", description = "Checks if an archive contains path traversal")
    public Integer checkPathTraversal(
            @Parameters(index = "0", paramLabel = "ARCHIVE", description = "Path to archive") Path archive)
            throws IOException {

        return parseArchive(archive).containsPathTraversal()
                ? ExitCode.SOFTWARE
                : ExitCode.OK;
    }

    @Command(name = "extract", description = "Extracts an archive, checking for path traversal")
    public Integer extract(
            @Parameters(index = "0", paramLabel = "ARCHIVE", description = "Path to archive") Path archive,
            @Parameters(index = "1", paramLabel = "DESTINATION", description = "Output destination") Path destination,
            @Option(names = "--no-overwrite", description = "Do not overwrite existing files") boolean noOverwrite)
            throws ArchiveException, IOException {

        parseArchive(archive).extract(destination, !noOverwrite);
        return ExitCode.OK;
    }

    private static Archive parseArchive(Path archive) throws IOException {
        if (!Files.exists(archive)) {
            throw new InvalidParameterException("File does not exist at: " + archive.toAbsolutePath());
        }

        try {
            return Archive.parseArchive(archive);
        } catch (IllegalArgumentException | IOException e) {
            throw new InvalidParameterException("File is not an archive/zip: " + archive.toAbsolutePath());
        }
    }
}