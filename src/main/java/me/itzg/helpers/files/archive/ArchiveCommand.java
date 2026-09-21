package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveException;

import me.itzg.helpers.errors.InvalidParameterException;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/** Commands for checking archive paths and extracting files. */
@Command(name = "archive", description = "Archive helpers, checking for path traversal and extracting archives")
public class ArchiveCommand {
    @Option(names = { "--help", "-h" }, usageHelp = true)
    boolean help;

    /**
     * Checks for path traversal without extracting files.
     *
     * @param archive source archive path
     * @return {@link ExitCode#OK} when no traversal is found, or {@link ExitCode#SOFTWARE} otherwise
     * @throws IOException if reading the archive fails
     * @throws InvalidParameterException if the source is missing or cannot be identified as a supported archive
     */
    @Command(name = "check-path-traversal", description = "Checks if an archive contains path traversal attack")
    public Integer checkPathTraversal(
            @Parameters(index = "0", paramLabel = "ARCHIVE", description = "Path to archive") Path archive)
            throws IOException {

        return parseArchive(archive).containsPathTraversal()
                ? ExitCode.SOFTWARE
                : ExitCode.OK;
    }

    /**
     * Extracts the requested files, or everything when no file names are supplied.
     *
     * @param archive source archive path
     * @param destination output directory
     * @param overwrite whether to replace existing files
     * @param files exact file names; {@code null} or empty extracts everything
     * @return {@link ExitCode#OK} after successful extraction
     * @throws ArchiveException if path traversal or missing requested files are detected
     * @throws IOException if reading or extracting the archive fails
     * @throws InvalidParameterException if the source is missing or cannot be identified as a supported archive
     * @see Archive#extract(Path, List, boolean)
     */
    @Command(name = "extract", description = "Extracts an archive, checking for path traversal")
    public Integer extract(
            @Parameters(index = "0", paramLabel = "ARCHIVE", description = "Path to archive") Path archive,
            @Parameters(index = "1", paramLabel = "DESTINATION", description = "Output destination") Path destination,
            @Option(names = "--overwrite", description = "Overwrite existing files") boolean overwrite,
            @Parameters(index = "2..*", arity = "0..*", paramLabel = "FILE",
                    description = "Exact archive file paths to extract; extracts everything when omitted") List<String> files)
            throws ArchiveException, IOException {

        final Archive source = parseArchive(archive);
        if (files == null || files.isEmpty()) {
            source.extract(destination, overwrite);
        } else {
            source.extract(destination, files, overwrite);
        }
        return ExitCode.OK;
    }

    /**
     * Creates an archive helper, reporting missing files and unsupported types as parameter errors.
     *
     * @param archive source archive path
     * @return an archive helper with the appropriate reader
     * @throws InvalidParameterException if the file is missing or its type is not recognized
     */
    private static Archive parseArchive(Path archive) throws IOException {
        if (!Files.exists(archive)) {
            throw new InvalidParameterException("File does not exist at: " + archive.toAbsolutePath());
        }

        try {
            return ArchiveFactory.create(archive);
        } catch (IllegalArgumentException | IOException e) {
            throw new InvalidParameterException("File is not an archive/zip: " + archive.toAbsolutePath());
        }
    }
}
