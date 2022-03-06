package me.itzg.helpers.get;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.HttpEntity;

@Slf4j
public class SaveToFileHandler extends LoggingResponseHandler<Path> implements OutputResponseHandler {

  private final Path outputFile;
  private final boolean logProgressEach;

  public SaveToFileHandler(Path outputFile, boolean logProgressEach) {
    this.outputFile = outputFile;
    this.logProgressEach = logProgressEach;
  }

  @Override
  public Path handleEntity(HttpEntity entity) throws IOException {
    try (OutputStream out = Files.newOutputStream(outputFile)) {
      entity.writeTo(out);
    }
    if (logProgressEach) {
      log.info("Downloaded {}", outputFile);
    }
    return outputFile;
  }
}
