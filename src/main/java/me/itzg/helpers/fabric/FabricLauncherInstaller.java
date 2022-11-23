package me.itzg.helpers.fabric;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class FabricLauncherInstaller {

    private final Path outputDir;
    private final Path resultsFile;


    public void installGivenVersions() {

    }

    public void installGivenFile(Path launcher) throws IOException {
        if (resultsFile != null) {
            new ResultsFile(resultsFile)
                .launcher(launcher)
                .populate();
        }
    }

    public void installGivenUri(URI installerUri) {

    }

}
