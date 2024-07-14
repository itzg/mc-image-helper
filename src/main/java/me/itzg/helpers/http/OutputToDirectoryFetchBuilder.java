package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.ReactiveFileUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ByteBufFlux;
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

    private FileDownloadStatusHandler statusHandler = (status, uri, file) -> {
    };
    private FileDownloadedHandler downloadedHandler = (uri, file, contentSizeBytes) -> {
    };

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

    public Mono<Path> assemble() {
        return useReactiveClient(client ->
            client
                .headers(this::applyHeaders)
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "file head fetch"))
                .head()
                .uri(uri())
                .responseSingle((resp, bodyMono) -> {
                    if (notSuccess(resp)) {
                        return failedRequestMono(resp, bodyMono, "Extracting filename");
                    }

                    if (isHeadUnsupportedResponse(resp)) {
                        return assembleFileDownloadNameViaGet(client);
                    }

                    final Path outputFile = outputDirectory.resolve(extractFilename(resp));
                    final Instant lastModified = respLastModified(resp);

                    return assembleFileDownload(client, outputFile,
                        lastModified,
                        resp.resourceUrl()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .checkpoint("Fetch HEAD of requested file")
        );
    }

    private Mono<Path> assembleFileDownloadNameViaGet(HttpClient client) {
        return client
            .followRedirect(true)
            .doOnRequest(debugLogRequest(log, "file get fetch"))
            .get()
            .uri(uri())
            .response((resp, byteBufFlux) -> {
                if (notSuccess(resp)) {
                    return failedRequestMono(resp, byteBufFlux.aggregate(), "Getting file");
                }

                final Path outputFile = outputDirectory.resolve(extractFilename(resp));
                statusHandler.call(FileDownloadStatus.DOWNLOADING, uri(), outputFile);

                return skipExisting(resp, outputFile)
                    .flatMap(skip -> skip ? Mono.just(outputFile)
                        : copyBodyInputStreamToFile(byteBufFlux, outputFile)
                    );
            })
            .last();
    }

    private Instant respLastModified(HttpClientResponse resp) {
        final Long lastModified = resp.responseHeaders().getTimeMillis(LAST_MODIFIED);

        return lastModified != null ? Instant.ofEpochMilli(lastModified) : null;
    }

    /**
     * @return the file if it can be skipped or empty mono if not
     */
    private Mono<Boolean> skipExisting(HttpClientResponse resp, @NotNull Path outputFile) {
        return ReactiveFileUtils.fileExists(outputFile)
            .flatMap(exists -> {
                if (!exists) {
                    return Mono.just(false);
                }

                if (skipExisting && !skipUpToDate) {
                    log.debug("The file {} already exists", outputFile);
                    statusHandler.call(FileDownloadStatus.SKIP_FILE_EXISTS, uri(), outputFile);
                    return Mono.just(true);
                }
                else if (skipUpToDate) {

                    return ReactiveFileUtils.getLastModifiedTime(outputFile)
                        .mapNotNull(outputLastModified -> {
                            final Instant headerLastModified = respLastModified(resp);
                            if (isFileUpToDate(outputLastModified, headerLastModified)) {
                                log.debug("The file={} lastModified={} is already up to date compared to response={}",
                                    outputFile, outputLastModified, headerLastModified
                                );
                                statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri(), outputFile);
                                return true;
                            }

                            return false;
                        });
                }

                return Mono.just(false);
            });

    }

    private static boolean isFileUpToDate(Instant fileLastModified, Instant headerLastModified) {
        return headerLastModified != null && headerLastModified.compareTo(fileLastModified) <= 0;
    }

    private Mono<Path> assembleFileDownload(HttpClient client, Path outputFile, Instant headerLastModified, String resourceUrl) {
        final Mono<Instant> fileLastModifiedMono = ReactiveFileUtils.getLastModifiedTime(outputFile);

        final Mono<Boolean> alreadyUpToDateMono;
        if (skipExisting && !skipUpToDate) {

            alreadyUpToDateMono = ReactiveFileUtils.fileExists(outputFile)
                .doOnNext(exists -> {
                    if (exists) {
                        log.debug("The file {} already exists", outputFile);
                        statusHandler.call(FileDownloadStatus.SKIP_FILE_EXISTS, uri(), outputFile);
                    }
                });
        }
        else if (skipUpToDate) {

            alreadyUpToDateMono =
                fileLastModifiedMono
                    .map(fileLastModified -> {
                        final boolean fileUpToDate = isFileUpToDate(fileLastModified, headerLastModified);
                        if (fileUpToDate) {
                            log.debug("The file={} lastModified={} is already up to date compared to response={}",
                                outputFile, fileLastModified, headerLastModified
                            );
                            statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri(), outputFile);
                        }
                        return fileUpToDate;
                    })
                    .defaultIfEmpty(false);
        }
        else {

            alreadyUpToDateMono = Mono.just(false);
        }

        return alreadyUpToDateMono
            .filter(alreadyUpToDate -> !alreadyUpToDate)
            .flatMap(notUsed -> client
                .headers(this::applyHeaders)
                .headersWhen(headers ->
                    skipUpToDate ?
                        fileLastModifiedMono
                            .map(outputLastModified -> headers.set(
                                IF_MODIFIED_SINCE,
                                httpDateTimeFormatter.format(outputLastModified)
                            ))
                            .defaultIfEmpty(headers)
                        : Mono.just(headers)
                )
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "file fetch"))
                .doOnRequest(
                    (httpClientRequest, connection) -> statusHandler.call(FileDownloadStatus.DOWNLOADING, uri(), outputFile))
                .get()
                .uri(resourceUrl)
                .response((resp, byteBufFlux) -> {
                    if (skipUpToDate && resp.status() == HttpResponseStatus.NOT_MODIFIED) {
                        log.debug("The file {} is already up to date", outputFile);
                        statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri(), outputFile);
                        return Mono.just(outputFile);
                    }

                    if (notSuccess(resp)) {
                        return failedRequestMono(resp, byteBufFlux.aggregate(), "Downloading file");
                    }

                    return copyBodyInputStreamToFile(byteBufFlux, outputFile);
                })
                .last()
                .checkpoint("Fetching file into directory")
            )
            .defaultIfEmpty(outputFile);
    }

    private Mono<Path> copyBodyInputStreamToFile(ByteBufFlux byteBufFlux, Path outputFile) {
        log.trace("Copying response body to file={}", outputFile);

        return ReactiveFileUtils.copyByteBufFluxToFile(byteBufFlux, outputFile)
            .map(fileSize -> {
                statusHandler.call(FileDownloadStatus.DOWNLOADED, uri(), outputFile);
                downloadedHandler.call(uri(), outputFile, fileSize);
                return outputFile;
            });
    }

    private String extractFilename(HttpClientResponse resp) {
        final String contentDisposition = resp.responseHeaders().get(HttpHeaderNames.CONTENT_DISPOSITION);
        final String dispositionFilename = FilenameExtractor.filenameFromContentDisposition(contentDisposition);
        if (dispositionFilename != null) {
            return dispositionFilename;
        }

        final int pos = resp.path().lastIndexOf('/');
        return resp.path().substring(pos + 1);
    }

}
