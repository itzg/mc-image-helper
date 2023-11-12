package me.itzg.helpers.files;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes a key=value file suitable for sourcing as environment variables
 * (such as {@code set -a }) in a shell context
 */
@Slf4j
public class ResultsFileWriter implements AutoCloseable {

    public static final String OPTION_DESCRIPTION =
        "A key=value file suitable for scripted environment variables. Currently includes"
            + "\n  SERVER: the entry point jar or script";
    public static final String MODPACK_NAME = "MODPACK_NAME";
    public static final String MODPACK_VERSION = "MODPACK_VERSION";
    private final BufferedWriter writer;

    public ResultsFileWriter(Path resultsFile) throws IOException {
        this(resultsFile, false);
    }

    public ResultsFileWriter(Path resultsFile, boolean append) throws IOException {
        final OpenOption[] options = append ?
            new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
            : new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};

        writer = Files.newBufferedWriter(resultsFile, options);
    }

    public ResultsFileWriter write(String field, String value) throws IOException {
        log.debug("Writing {}=\"{}\" to results file", field, value);
        writer.write(String.format("%s=\"%s\"", field, value));
        writer.newLine();
        return this;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @SuppressWarnings("UnusedReturnValue")
    public ResultsFileWriter writeServer(Path serverJar) throws IOException {
        return write("SERVER", serverJar.toString());
    }

    @SuppressWarnings("UnusedReturnValue")
    public ResultsFileWriter writeVersion(String version) throws IOException {
        return write("VERSION", version);
    }

    @SuppressWarnings("UnusedReturnValue")
    public ResultsFileWriter writeType(String type) throws IOException {
        return write("TYPE", type);
    }
}
