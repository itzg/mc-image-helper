package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.LAST_MODIFIED;
import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpHeaderNames;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.ReactiveFileUtils;
import org.apache.hc.client5.http.async.methods.AbstractBinResponseConsumer;
import org.apache.hc.client5.http.async.methods.SimpleRequestBuilder;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
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
                    if (isHeadUnsupportedResponse(resp)) {
                        return assembleFileDownloadNameViaGet(client);
                    }

                    if (notSuccess(resp)) {
                        return failedRequestMono(resp, bodyMono, "Extracting filename");
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
            .flatMap(notUsed -> {
                final SimpleRequestBuilder reqBuilder = SimpleRequestBuilder.get(resourceUrl);
                applyHeaders(reqBuilder);

                return
                    fileLastModifiedMono
                        .map(instant -> reqBuilder.setHeader("If-Modified-Since", httpDateTimeFormatter.format(instant)))
                        .then(
                            Mono.<Path>create(sink -> {
                                getHcAsyncClient().execute(
                                    SimpleRequestProducer.create(reqBuilder.build()),
                                    new ResponseToFileConsumer(outputFile),
                                    new MonoSinkFutureCallbackAdapter<>(sink)
                                );
                            })
                        );
            })
            .defaultIfEmpty(outputFile);
    }

    private Mono<Path> copyBodyInputStreamToFile(ByteBufFlux byteBufFlux, Path outputFile) {
        log.trace("Copying response body to file={}", outputFile);

        return ReactiveFileUtils.writeByteBufFluxToFile(byteBufFlux, outputFile)
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

    private class ResponseToFileConsumer extends AbstractBinResponseConsumer<Path> {

        private final Path outputFile;

        private SeekableByteChannel channel;
        private long amount;

        public ResponseToFileConsumer(Path outputFile) {
            this.outputFile = outputFile;
        }

        @Override
        public void releaseResources() {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        protected int capacityIncrement() {
            return 4096;
        }

        @Override
        protected void data(ByteBuffer src, boolean endOfStream) throws IOException {
            if (channel != null) {
                amount += channel.write(src);
                if (endOfStream) {
                    channel.close();

                    statusHandler.call(FileDownloadStatus.DOWNLOADED, uri(), outputFile);
                    downloadedHandler.call(uri(), outputFile, amount);
                }
            }
        }

        @Override
        protected void start(HttpResponse response,
            ContentType contentType
        ) throws HttpException, IOException {
            if (skipUpToDate && response.getCode() == HttpStatus.SC_NOT_MODIFIED) {
                log.debug("The file {} is already up to date", outputFile);
                statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri(), outputFile);
                return;
            }

            statusHandler.call(FileDownloadStatus.DOWNLOADING, uri(), outputFile);

            channel = Files.newByteChannel(outputFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        }

        @Override
        protected Path buildResult() {
            return outputFile;
        }
    }
}
