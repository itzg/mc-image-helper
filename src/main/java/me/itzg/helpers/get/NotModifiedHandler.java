package me.itzg.helpers.get;

import java.io.IOException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;

@RequiredArgsConstructor
public class NotModifiedHandler implements OutputResponseHandler {
  private final Path file;
  private final OutputResponseHandler delegate;

  @Override
  public Path handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    return response.getCode() == HttpStatus.SC_NOT_MODIFIED ? file
        : delegate.handleResponse(response);
  }
}
