package me.itzg.helpers.get;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;

@Slf4j
class OutputToDirectoryHandler implements OutputResponseHandler {

  private final Path directory;
  final FilenameExtractor filenameExtractor;

  public OutputToDirectoryHandler(Path directory,
      LatchingUrisInterceptor interceptor) {
    this.directory = directory;
    filenameExtractor = new FilenameExtractor(interceptor);
  }

  @Override
  public Path handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    final String filename = filenameExtractor.extract(response);

    final Path filePath = directory.resolve(filename);

    log.debug("Writing response content to path={}", filePath);
    try (OutputStream out = Files.newOutputStream(filePath)) {
      response.getEntity().writeTo(out);
    }

    return filePath;
  }
}
