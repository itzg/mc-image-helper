package me.itzg.helpers.http;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FilenameExtractor {
    private static final Pattern RFC_2047_ENCODED = Pattern.compile("=\\?UTF-8\\?Q\\?(.+)\\?=");

  private final LatchingUrisInterceptor interceptor;

  public FilenameExtractor(LatchingUrisInterceptor interceptor) {
    this.interceptor = Objects.requireNonNull(interceptor, "interceptor is required");
  }

  static String filenameFromContentDisposition(String headerValue) {
      if (headerValue == null) {
          return null;
      }

      final ContentType parsed = ContentType.parse(headerValue);
      log.debug("Response has contentDisposition={}", headerValue);
      final String filename = parsed.getParameter("filename");
      final Matcher m = RFC_2047_ENCODED.matcher(filename);
      if (m.matches()) {
          return m.group(1);
      }
      return filename;
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
      filename = filenameFromContentDisposition(contentDisposition.getValue());
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
