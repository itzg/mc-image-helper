package me.itzg.helpers.fabric;

import static me.itzg.helpers.http.Fetch.fetch;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.http.UriBuilder;

@RequiredArgsConstructor
@Slf4j
public class FabricLauncherInstaller {

    private static final String RESULT_LAUNCHER = "LAUNCHER";

    private final Path outputDir;
    private final Path resultsFile;

    private String fabricMetaBaseUrl = "https://meta.fabricmc.net";

    public void installGivenVersions(@NonNull String minecraftVersion, String loaderVersion, String installerVersion)
        throws IOException {
        final UriBuilder uriBuilder = UriBuilder.withBaseUrl(fabricMetaBaseUrl);
        final List<LoaderResponseEntry> loaderResponse = fetch(
            uriBuilder.resolve("/v2/versions/loader/{game_version}", minecraftVersion))
            .toObjectList(LoaderResponseEntry.class)
            .execute();

        //TODO
    }

    public void installGivenFile(Path launcher) throws IOException {
        log.debug("Not using output directory since launcher file is provided");
        if (resultsFile != null) {
            try (ResultsFileWriter writer = new ResultsFileWriter(resultsFile)) {
                writer.write(RESULT_LAUNCHER, launcher.toString());
            }
        }
    }

    public void installGivenUri(URI installerUri) {

    }

}
