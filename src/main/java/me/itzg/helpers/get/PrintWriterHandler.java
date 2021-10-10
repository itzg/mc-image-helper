package me.itzg.helpers.get;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

class PrintWriterHandler extends AbstractHttpClientResponseHandler<String> {
  private final PrintWriter writer;

  public PrintWriterHandler(PrintWriter writer) {
    this.writer = writer;
  }

  @Override
  public String handleEntity(HttpEntity entity) throws IOException {

    final byte[] buffer = new byte[1024];
    try (InputStream content = entity.getContent()) {
      int length;
      while ((length = content.read(buffer)) >= 0) {
        writer.print(new String(buffer, 0, length, StandardCharsets.UTF_8));
      }
    }
    writer.flush();

    // no filename to return
    return "";
  }
}
