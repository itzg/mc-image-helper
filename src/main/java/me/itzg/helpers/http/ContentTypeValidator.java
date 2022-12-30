package me.itzg.helpers.http;

import java.io.IOException;
import java.util.List;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.ProtocolException;

public class ContentTypeValidator {
  final List<String> expectedContentTypes;

  public ContentTypeValidator(List<String> expectedContentTypes) {
    this.expectedContentTypes = expectedContentTypes;
  }

  public void validate(ClassicHttpResponse response) throws IOException {
    if (response.getCode() != 200) {
      return;
    }

    final String contentType;
    try {
      contentType = response.getHeader(HttpHeaders.CONTENT_TYPE).getValue();
    } catch (ProtocolException e) {
      throw new IOException("Missing content type header", e);
    }
    final String parsedContentType = ContentType.parse(contentType).getMimeType();

    if (expectedContentTypes.stream()
        .noneMatch(parsedContentType::equalsIgnoreCase)) {
      throw new IOException(
          String.format("Unexpected content type '%s', expected any of %s", parsedContentType,
              expectedContentTypes));
    }
  }
}
