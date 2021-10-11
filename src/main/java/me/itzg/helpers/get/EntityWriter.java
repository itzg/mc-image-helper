package me.itzg.helpers.get;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.apache.hc.core5.http.HttpEntity;

class EntityWriter {

  static void write(HttpEntity entity, Writer writer) throws IOException {
    final byte[] buffer = new byte[1024];
    try (InputStream content = entity.getContent()) {
      int length;
      while ((length = content.read(buffer)) >= 0) {
        writer.write(new String(buffer, 0, length, StandardCharsets.UTF_8));
      }
    }
    writer.flush();
  }
}
