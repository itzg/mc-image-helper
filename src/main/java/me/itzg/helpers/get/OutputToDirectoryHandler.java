package me.itzg.helpers.get;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

@Slf4j
class OutputToDirectoryHandler implements HttpClientResponseHandler<String> {
  final Path directory;
  final LatchingUrisInterceptor interceptor;

  public OutputToDirectoryHandler(Path directory,
      LatchingUrisInterceptor interceptor) {
    this.directory = directory;
    this.interceptor = interceptor;
  }

  @Override
  public String handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    // Same as AbstractHttpClientResponseHandler
    if (response.getCode() >= HttpStatus.SC_REDIRECTION) {
      EntityUtils.consume(response.getEntity());
      throw new HttpResponseException(response.getCode(), response.getReasonPhrase());
    }

    final Header contentDisposition = response
        .getHeader("content-disposition");

    String filename = null;
    if (contentDisposition != null) {
      final ContentType parsed = ContentType.parse(contentDisposition.getValue());
      log.debug("Response has contentDisposition={}", contentDisposition);
      filename = parsed.getParameter("filename");
    }
    if (filename == null) {
      final String path = interceptor.getLastRequestedUri().getPath();
      log.debug("Deriving filename from response path={}", path);
      final int pos = path.lastIndexOf('/');
      filename = path.substring(pos >= 0 ? pos + 1 : 0);
    }

    final Path filePath = directory.resolve(filename);
    log.debug("Writing response content to path={}", filePath);
    try (OutputStream out = Files.newOutputStream(filePath)) {
      response.getEntity().writeTo(out);
    }

    return filename;
  }
}
