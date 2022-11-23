package me.itzg.helpers.fabric;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ResultsFile {

    private final Path resultsFile;
    private Path launcher;

    public ResultsFile(Path resultsFile) {
        this.resultsFile = resultsFile;
    }

    public ResultsFile launcher(Path launcher) {
        this.launcher = launcher;
        return this;
    }

    public void populate() throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(resultsFile)) {
            writer.write("LAUNCHER="+launcher.toFile());
            writer.newLine();
        }

    }
}
