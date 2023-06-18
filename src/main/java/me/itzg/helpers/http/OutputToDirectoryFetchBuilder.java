package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import org.jetbrains.annotations.Blocking;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

@Slf4j
@Accessors(fluent = true)
public class OutputToDirectoryFetchBuilder extends FetchBuilderBase<OutputToDirectoryFetchBuilder> {

    private final Path outputDirectory;

    @Setter
    private boolean skipExisting;

    @Setter
    private boolean skipUpToDate;

    private FileDownloadStatusHandler statusHandler = (status, uri, file) -> {};
    private FileDownloadedHandler downloadedHandler = (uri, file, contentSizeBytes) -> {};

    protected OutputToDirectoryFetchBuilder(State state, Path outputDirectory) {
        super(state);

        if (!Files.isDirectory(outputDirectory)) {
            throw new IllegalArgumentException(outputDirectory + " is not a directory or does not exist");
        }
        this.outputDirectory = outputDirectory;
    }

    @SuppressWarnings("unused")
    public OutputToDirectoryFetchBuilder handleStatus(FileDownloadStatusHandler statusHandler) {
        requireNonNull(statusHandler);
        this.statusHandler = statusHandler;
        return self();
    }

    @SuppressWarnings("unused")
    public OutputToDirectoryFetchBuilder handleDownloaded(FileDownloadedHandler downloadedHandler) {
        requireNonNull(downloadedHandler);
        this.downloadedHandler = downloadedHandler;
        return self();
    }

    public Path execute() throws IOException {
        return assemble()
            .block();
    }

    @RequiredArgsConstructor
    private static class FileToDownload {
        final Path outputFile;
        final Instant lastModified;
    }

    public Mono<Path> assemble() {
        //noinspection BlockingMethodInNonBlockingContext
        return useReactiveClient(client ->
            client
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "file head fetch"))
                .head()
                .uri(uri())
                .responseSingle((resp, bodyMono) -> {
                    if (notSuccess(resp)) {
                        return failedRequestMono(resp, bodyMono, "Extracting filename");
                    }

                    final Path outputFile = outputDirectory.resolve(extractFilename(resp));
                    final Long lastModified = resp.responseHeaders().getTimeMillis(LAST_MODIFIED);

                    return Mono.just(new FileToDownload(outputFile,
                        lastModified != null ? Instant.ofEpochMilli(lastModified) : null
                    ));
                })
                .publishOn(Schedulers.boundedElastic())
                .checkpoint("Fetch HEAD of requested file")
                .flatMap(fileToDownload ->
                    assembleFileDownload(client, fileToDownload)
                )
        );
    }

    @Blocking
    private Mono<Path> assembleFileDownload(HttpClient client, FileToDownload fileToDownload) {
        final Path outputFile = fileToDownload.outputFile;

        if (skipExisting && !skipUpToDate && Files.exists(outputFile)) {
            log.debug("The file {} already exists", outputFile);
            statusHandler.call(FileDownloadStatus.SKIP_FILE_EXISTS, uri(), outputFile);
            return Mono.just(outputFile);
        }

        final boolean useIfModifiedSince = skipUpToDate && Files.exists(outputFile);
        final Instant outputLastModified;
        if (useIfModifiedSince) {
            try {
                //noinspection BlockingMethodInNonBlockingContext
                outputLastModified = Files.getLastModifiedTime(outputFile).toInstant();

                // Some endpoints don't support if-modified-since, but do provide the
                // last-modified of the HEAD'ed file. Can skip the retrieval here, if so.
                if (fileToDownload.lastModified != null
                    && fileToDownload.lastModified.isBefore(outputLastModified)) {

                    log.debug("The file={} lastModified={} is already up to date compared to response={}",
                        outputFile, outputLastModified, fileToDownload.lastModified);
                    statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri(), outputFile);
                    return Mono.just(outputFile);
                }
            } catch (IOException e) {
                return Mono.error(new GenericException("Unable to get last modified time of " + outputFile, e));
            }
        }
        else {
            outputLastModified = null;
        }

        statusHandler.call(FileDownloadStatus.DOWNLOADING, uri(), outputFile);

        return client
            .headers(headers -> {
                if (useIfModifiedSince) {
                    headers.set(
                        IF_MODIFIED_SINCE,
                        httpDateTimeFormatter.format(outputLastModified)
                    );
                }
            })
            .followRedirect(true)
            .doOnRequest(debugLogRequest(log, "file fetch"))
            .get()
            .uri(uri())
            .responseSingle((resp, bodyMono) -> {
                if (useIfModifiedSince && resp.status() == HttpResponseStatus.NOT_MODIFIED) {
                    log.debug("The file {} is already up to date", outputFile);
                    statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri(), outputFile);
                    return Mono.just(outputFile);
                }

                if (notSuccess(resp)) {
                    return failedRequestMono(resp, bodyMono, "Downloading file");
                }

                return bodyMono.asInputStream()
                    .publishOn(Schedulers.boundedElastic())
                    .flatMap(inputStream -> {
                        try {
                            @SuppressWarnings("BlockingMethodInNonBlockingContext") // false warning, see above
                            final long size = Files.copy(inputStream, outputFile, StandardCopyOption.REPLACE_EXISTING);
                            statusHandler.call(FileDownloadStatus.DOWNLOADED, uri(), outputFile);
                            downloadedHandler.call(uri(), outputFile, size);
                            return Mono.just(outputFile);
                        } catch (IOException e) {
                            return Mono.error(e);
                        }
                    });
            })
            .checkpoint("Fetching file into directory");
    }

    private String extractFilename(HttpClientResponse resp) {
        final String contentDisposition = resp.responseHeaders().get(HttpHeaderNames.CONTENT_DISPOSITION);
        final String dispositionFilename = FilenameExtractor.filenameFromContentDisposition(contentDisposition);
        if (dispositionFilename != null) {
            return dispositionFilename;
        }
        if (resp.redirectedFrom().length > 0) {
            final String lastUrl = resp.redirectedFrom()[resp.redirectedFrom().length - 1];
            final int pos = lastUrl.lastIndexOf('/');
            return lastUrl.substring(pos + 1);
        }
        final int pos = resp.path().lastIndexOf('/');
        return resp.path().substring(pos + 1);
    }

}
