package me.itzg.helpers.get;

import java.io.IOException;
import java.io.PrintWriter;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

class PrintWriterHandler extends AbstractHttpClientResponseHandler<String> {
  private final PrintWriter writer;

  public PrintWriterHandler(PrintWriter writer) {
    this.writer = writer;
  }

  @Override
  public String handleEntity(HttpEntity entity) throws IOException {
    try {

      writer.print(
          EntityUtils.toString(entity)
      );

    } catch (ParseException e) {
      throw new IOException("Failed to read entity into string", e);
    }

    // no filename to return
    return "";
  }
}
