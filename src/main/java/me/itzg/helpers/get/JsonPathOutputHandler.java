package me.itzg.helpers.get;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.PathNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import me.itzg.helpers.http.LoggingResponseHandler;
import me.itzg.helpers.http.OutputResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

class JsonPathOutputHandler extends LoggingResponseHandler<Path> implements OutputResponseHandler {

  private final PrintWriter writer;
  private final String jsonPath;
  private final String jsonValueWhenMissing;
  private final ParseContext parseContext;

  public JsonPathOutputHandler(PrintWriter writer, String jsonPath,
      String jsonValueWhenMissing) {
    this.writer = writer;
    // adapt jq style path into JsonPath
    this.jsonPath = jsonPath.startsWith("$") ? jsonPath : "$"+jsonPath;
    this.jsonValueWhenMissing = jsonValueWhenMissing;
    this.parseContext = JsonPath.using(
        Configuration.builder()
            .options(Option.SUPPRESS_EXCEPTIONS)
            .build()
    );
  }

  @Override
  public Path handleEntity(HttpEntity entity) throws IOException {

    final DocumentContext doc = parseContext.parse(entity.getContent());

    // first try as an atom converted to a string
    String result = doc.read(jsonPath, String.class);
    if (result == null) {
      // might be an indefinite query, so retry as a list
      final List<Object> results = doc.read(jsonPath);
      if (results == null) {
        // leaf is missing
        if (jsonValueWhenMissing != null) {
          result = jsonValueWhenMissing;
        }
        else {
          throw new PathNotFoundException("Missing property in path "+jsonPath);
        }
      }
      else if (!results.isEmpty()) {
        result = results.stream()
            .map(Object::toString)
            .collect(Collectors.joining(","));
      }
    }

    writer.println(result);

    // no filename to return
    return null;
  }

}
