package me.itzg.helpers.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpException;

@Slf4j
public class OutputToDirectoryHandler implements OutputResponseHandler {

  private final Path directory;
  final FilenameExtractor filenameExtractor;
  private final boolean logProgressEach;
  private ContentTypeValidator contentTypeValidator;

  public OutputToDirectoryHandler(Path directory,
      LatchingUrisInterceptor interceptor, boolean logProgressEach) {
    this.directory = directory;
    filenameExtractor = new FilenameExtractor(interceptor);
    this.logProgressEach = logProgressEach;
  }

  @Override
  public void setExpectedContentTypes(List<String> contentTypes) {
    this.contentTypeValidator = new ContentTypeValidator(contentTypes);
  }

  @Override
  public Path handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
    LoggingResponseHandler.logResponse(log, response);
    if (contentTypeValidator != null) {
      contentTypeValidator.validate(response);
    }

    final String filename = filenameExtractor.extract(response);

    final Path filePath = directory.resolve(filename);

    if (logProgressEach) {
      log.info("Downloaded {}", filePath);
    }
    else {
      log.debug("Writing response content to path={}", filePath);
    }
    try (OutputStream out = Files.newOutputStream(filePath)) {
      response.getEntity().writeTo(out);
    }

    return filePath;
  }
}
