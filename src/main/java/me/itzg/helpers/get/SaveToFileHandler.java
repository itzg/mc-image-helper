package me.itzg.helpers.get;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

public class SaveToFileHandler extends AbstractHttpClientResponseHandler<Path> implements OutputResponseHandler {

  private final Path outputFile;

  public SaveToFileHandler(Path outputFile) {
    this.outputFile = outputFile;
  }

  @Override
  public Path handleEntity(HttpEntity entity) throws IOException {
    try (OutputStream out = Files.newOutputStream(outputFile)) {
      entity.writeTo(out);
    }
    return outputFile;
  }
}
