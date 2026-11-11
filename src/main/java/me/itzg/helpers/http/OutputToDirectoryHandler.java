package me.itzg.helpers.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    // Write beside the target and move it into place, so an interrupted download never leaves a
    // short file under the real name for --skip-existing to keep.
    final Path tempDownloadFile = filePath.resolveSibling(filename + ".download");
    try (OutputStream out = Files.newOutputStream(tempDownloadFile)) {
      response.getEntity().writeTo(out);
    } catch (IOException e) {
      try {
        Files.deleteIfExists(tempDownloadFile);
      } catch (IOException suppressed) {
        e.addSuppressed(suppressed);
      }
      throw e;
    }
    Files.move(tempDownloadFile, filePath, StandardCopyOption.REPLACE_EXISTING);

    return filePath;
  }
}
