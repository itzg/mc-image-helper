package me.itzg.helpers.http;

import java.io.IOException;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;

@Slf4j
public class NotModifiedHandler implements OutputResponseHandler {
  private final Path file;
  private final OutputResponseHandler delegate;
  private final boolean logProgressEach;

  public NotModifiedHandler(Path file, OutputResponseHandler delegate, boolean logProgressEach) {
    this.file = file;
    this.delegate = delegate;
    this.logProgressEach = logProgressEach;
  }

  @Override
  public Path handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    LoggingResponseHandler.logResponse(log, response);

    if (response.getCode() == HttpStatus.SC_NOT_MODIFIED) {
      if (logProgressEach) {
        log.info("Skipping {} since it is already up to date", file);
      } else {
        log.debug("Skipping {} since it is already up to date", file);
      }
      return file;
    }
    return delegate.handleResponse(response);
  }
}
