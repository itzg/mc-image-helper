package me.itzg.helpers.get;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

class JsonPathOutputHandler extends AbstractHttpClientResponseHandler<Path> implements OutputResponseHandler {
  private final PrintWriter writer;
  private final String jsonPath;

  public JsonPathOutputHandler(PrintWriter writer, String jsonPath) {
    this.writer = writer;
    // adapt jq style path into JsonPath
    this.jsonPath = jsonPath.startsWith("$") ? jsonPath : "$"+jsonPath;
  }

  @Override
  public Path handleEntity(HttpEntity entity) throws IOException {

    final String result = JsonPath.parse(entity.getContent())
        .read(jsonPath, String.class);

    writer.println(result);

    // no filename to return
    return null;
  }

}
