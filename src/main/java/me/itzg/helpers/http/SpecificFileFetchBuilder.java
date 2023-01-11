package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class SpecificFileFetchBuilder extends FetchBuilderBase<SpecificFileFetchBuilder> {

    private final static DateTimeFormatter httpDateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));
    private FileDownloadStatusHandler statusHandler = (status, uri, p) -> {
    };
    private FileDownloadedHandler downloadedHandler = (uri, p, contentSizeBytes) -> {
    };


    private final Path file;
    private boolean skipUpToDate;
    private HttpClientResponseHandler<Path> handler;
    private boolean skipExisting;

    public SpecificFileFetchBuilder(State state, Path file) {
        super(state);
        this.file = file;
    }

    public SpecificFileFetchBuilder skipUpToDate(boolean skipUpToDate) {
        this.skipUpToDate = skipUpToDate;
        return self();
    }

    public SpecificFileFetchBuilder skipExisting(boolean skipExisting) {
        this.skipExisting = skipExisting;
        return self();
    }

    public SpecificFileFetchBuilder handleStatus(FileDownloadStatusHandler handler) {
        requireNonNull(handler);
        this.statusHandler = handler;
        return self();
    }

    public SpecificFileFetchBuilder handleDownloaded(FileDownloadedHandler handler) {
        requireNonNull(handler);
        this.downloadedHandler = handler;
        return self();
    }

    public Path execute() throws IOException {
        return assemble()
            .block();
    }

    public Mono<Path> assemble() throws IOException {
        final URI uri = uri();

        if (skipExisting && Files.exists(file)) {
            log.debug("File already exists and skip requested");
            statusHandler.call(FileDownloadStatus.SKIP_FILE_EXISTS, uri, file);
            return Mono.just(file);
        }

        final boolean useIfModifiedSince = skipUpToDate && Files.exists(file);

        return usePreparedFetch(sharedFetch ->
            sharedFetch.getReactiveClient()
                .doOnRequest((httpClientRequest, connection) -> {
                    statusHandler.call(FileDownloadStatus.DOWNLOADING, uri, file);
                })
                .headers(headers -> {
                    if (useIfModifiedSince) {
                        try {
                            final FileTime lastModifiedTime;
                            lastModifiedTime = Files.getLastModifiedTime(file);
                            headers.set(
                                IF_MODIFIED_SINCE.toString(),
                                httpDateTimeFormatter.format(lastModifiedTime.toInstant())
                            );
                        } catch (IOException e) {
                            throw new GenericException("Unable to get last modified time of " + file, e);
                        }

                    }
                })
                .followRedirect(true)
                .get()
                .uri(uri)
                .responseSingle((httpClientResponse, bodyMono) -> {
                    if (useIfModifiedSince
                        && httpClientResponse.status().equals(NOT_MODIFIED)) {
                        return Mono.just(file);
                    }
                    return bodyMono.asInputStream()
                        .publishOn(Schedulers.boundedElastic())
                        .flatMap(inputStream -> {
                            try {
                                final long size = Files.copy(inputStream, file, StandardCopyOption.REPLACE_EXISTING);
                                statusHandler.call(FileDownloadStatus.DOWNLOADED, uri, file);
                                downloadedHandler.call(uri, file, size);
                            } catch (IOException e) {
                                return Mono.error(e);
                            } finally {
                                try {
                                    inputStream.close();
                                } catch (IOException e) {
                                    log.warn("Unable to close body input stream", e);
                                }
                            }
                            return Mono.just(file);
                        });
                })
        );
    }

}
