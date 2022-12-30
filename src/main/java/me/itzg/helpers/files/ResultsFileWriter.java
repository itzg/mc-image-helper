package me.itzg.helpers.files;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a key=value file suitable for sourcing as environment variables
 * (such as {@code set -a }) in a shell context
 */
public class ResultsFileWriter implements AutoCloseable {

    public static final String OPTION_DESCRIPTION =
        "A key=value file suitable for scripted environment variables. Currently includes"
            + "\n  SERVER: the entry point jar or script";
    private final BufferedWriter writer;

    public ResultsFileWriter(Path resultsFile) throws IOException {
        writer = Files.newBufferedWriter(resultsFile);
    }

    public ResultsFileWriter write(String field, String value) throws IOException {
        writer.write(field);
        writer.write("=");
        writer.write(value);
        writer.newLine();
        return this;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
