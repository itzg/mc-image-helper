package me.itzg.helpers.get;

import java.io.IOException;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.http.LatchingUrisInterceptor;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

@Slf4j
public class FilenameExtractor {
  private final LatchingUrisInterceptor interceptor;

  public FilenameExtractor(LatchingUrisInterceptor interceptor) {
    this.interceptor = Objects.requireNonNull(interceptor, "interceptor is required");
  }

  public String extract(ClassicHttpResponse response) throws IOException, ProtocolException {
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

    return filename;
  }
}
