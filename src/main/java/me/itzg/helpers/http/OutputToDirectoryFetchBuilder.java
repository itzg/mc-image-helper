package me.itzg.helpers.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

@Slf4j
@Accessors(fluent = true)
public class OutputToDirectoryFetchBuilder extends FetchBuilder<OutputToDirectoryFetchBuilder> {

    private final Path outputDirectory;
    @Setter
    private boolean skipExisting;

    protected OutputToDirectoryFetchBuilder(Config config, Path outputDirectory) {
        super(config);

        if (!Files.isDirectory(outputDirectory)) {
            throw new IllegalArgumentException(outputDirectory + " is not a directory or does not exist");
        }
        this.outputDirectory = outputDirectory;
    }

    public Path execute() throws IOException {
        try (CloseableHttpClient client = client()) {
            final HttpHead headReq = head(false);
            final String filename = client.execute(headReq, new DeriveFilenameHandler());

            final Path outputFile = outputDirectory.resolve(filename);
            if (skipExisting && Files.exists(outputFile)) {
                log.debug("File {} already exists", outputFile);
                return outputFile;
            }

            final HttpGet getReq = get();
            return client.execute(getReq, new SaveToFileHandler(outputFile, false));
        }
    }
}
