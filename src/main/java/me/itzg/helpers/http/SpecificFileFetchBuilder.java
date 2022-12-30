package me.itzg.helpers.http;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicHttpRequest;

@Slf4j
public class SpecificFileFetchBuilder extends FetchBuilderBase<SpecificFileFetchBuilder> {
    private final static DateTimeFormatter httpDateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    private final Path file;
    private boolean skipUpToDate;
    private boolean logProgressEach = false;
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

    public SpecificFileFetchBuilder logProgressEach(boolean logProgressEach) {
        this.logProgressEach = logProgressEach;
        return self();
    }

    public Path execute() throws IOException {
        if (skipExisting && Files.exists(file)) {
            log.debug("File already exists and skip requested");
            return file;
        }

        return useClient(client -> client.execute(get(), handler));
    }

    @Override
    protected void configureRequest(BasicHttpRequest request) throws IOException {
        super.configureRequest(request);

        final SaveToFileHandler handler = new SaveToFileHandler(file, logProgressEach);
        handler.setExpectedContentTypes(getAcceptContentTypes());

        if (skipUpToDate && Files.exists(file)) {
            final FileTime lastModifiedTime = Files.getLastModifiedTime(file);
            request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                httpDateTimeFormatter.format(lastModifiedTime.toInstant())
            );

            // wrap the handler to intercept the NotModified response
            this.handler = new NotModifiedHandler(file, handler, logProgressEach);
        }
        else {
            this.handler = handler;
        }
    }
}
