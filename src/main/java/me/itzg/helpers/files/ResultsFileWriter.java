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

    private final BufferedWriter writer;

    public ResultsFileWriter(Path resultsFile) throws IOException {
        writer = Files.newBufferedWriter(resultsFile);
    }

    public void write(String field, String value) throws IOException {
        writer.write(field);
        writer.write("=");
        writer.write(value);
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
