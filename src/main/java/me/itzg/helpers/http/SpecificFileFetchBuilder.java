package me.itzg.helpers.http;

import static io.netty.handler.codec.http.HttpHeaderNames.IF_MODIFIED_SINCE;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_MODIFIED;
import static java.lang.System.currentTimeMillis;
import static java.util.Objects.requireNonNull;

import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.ReactiveFileUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Accessors(fluent = true)
public class SpecificFileFetchBuilder extends FetchBuilderBase<SpecificFileFetchBuilder> {

    private FileDownloadStatusHandler statusHandler = (status, uri, p) -> {};
    private FileDownloadedHandler downloadedHandler = (uri, p, contentSizeBytes) -> {};

    private final Path file;
    @Setter
    private boolean skipUpToDate;
    @Setter
    private boolean skipExisting;

    SpecificFileFetchBuilder(State state, Path file) {
        super(state);
        this.file = file;
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

    public Mono<Path> assemble() {
        final URI uri = uri();

        if (skipExisting && Files.exists(file)) {
            log.debug("Skipping file={} that already exists due to request", file);
            statusHandler.call(FileDownloadStatus.SKIP_FILE_EXISTS, uri, file);
            return Mono.just(file);
        }

        final boolean useIfModifiedSince = skipUpToDate && Files.exists(file);

        return useReactiveClient(client ->
            client
                .doOnRequest((httpClientRequest, connection) ->
                    statusHandler.call(FileDownloadStatus.DOWNLOADING, uri, file)
                )
                .headers(headers -> {
                    if (useIfModifiedSince) {
                        try {
                            final FileTime lastModifiedTime;
                            lastModifiedTime = Files.getLastModifiedTime(file);
                            headers.set(
                                IF_MODIFIED_SINCE,
                                httpDateTimeFormatter.format(lastModifiedTime.toInstant())
                            );
                        } catch (IOException e) {
                            throw new GenericException("Unable to get last modified time of " + file, e);
                        }

                    }

                    applyHeaders(headers);
                })
                .followRedirect(true)
                .doOnRequest(debugLogRequest(log, "file fetch"))
                .get()
                .uri(uri)
                .response((resp, byteBufFlux) -> {
                    final HttpResponseStatus status = resp.status();

                    if (useIfModifiedSince && status == NOT_MODIFIED) {
                        log.debug("The file {} is already up to date", file);
                        statusHandler.call(FileDownloadStatus.SKIP_FILE_UP_TO_DATE, uri, file);
                        return Mono.just(file);
                    }

                    if (notSuccess(resp)) {
                        return failedRequestMono(resp, byteBufFlux.aggregate(), "Trying to retrieve file");
                    }

                    if (notExpectedContentType(resp)) {
                        return failedContentTypeMono(resp);
                    }

                    return ReactiveFileUtils.copyByteBufFluxToFile(byteBufFlux, file)
                        .flatMap(fileSize -> {
                            statusHandler.call(FileDownloadStatus.DOWNLOADED, uri, file);
                            downloadedHandler.call(uri, file, fileSize);
                            return Mono
                                .deferContextual(contextView -> {
                                    if (log.isDebugEnabled()) {
                                        final long durationMillis =
                                            currentTimeMillis() - contextView.<Long>get("downloadStart");
                                        log.debug("Download of {} took {} at {}",
                                            uri, formatDuration(durationMillis), transferRate(durationMillis, fileSize)
                                        );
                                    }
                                    return Mono.just(file);
                                });
                        });

                })
                .last()
                .contextWrite(context -> context.put("downloadStart", currentTimeMillis()))
        );
    }

}
