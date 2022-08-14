package me.itzg.helpers.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import me.itzg.helpers.http.LoggingResponseHandler;
import me.itzg.helpers.http.OutputResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

class PrintWriterHandler extends LoggingResponseHandler<Path> implements OutputResponseHandler {
  private final PrintWriter writer;

  public PrintWriterHandler(PrintWriter writer) {
    this.writer = writer;
  }

  @Override
  public Path handleEntity(HttpEntity entity) throws IOException {

    EntityWriter.write(entity, writer);

    // no filename to return
    return null;
  }
}
