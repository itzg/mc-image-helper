package me.itzg.helpers.get;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.HttpEntity;

class JsonPathOutputHandler extends AbstractHttpClientResponseHandler<Path> implements OutputResponseHandler {

  protected static final TypeRef<List<String>> STRING_LIST_TYPE = new TypeRef<List<String>>() {
  };
  private final PrintWriter writer;
  private final String jsonPath;

  public JsonPathOutputHandler(PrintWriter writer, String jsonPath) {
    this.writer = writer;
    // adapt jq style path into JsonPath
    this.jsonPath = jsonPath.startsWith("$") ? jsonPath : "$"+jsonPath;
  }

  @Override
  public Path handleEntity(HttpEntity entity) throws IOException {

    final DocumentContext doc = JsonPath.parse(entity.getContent());

    // first try as an atom converted to a string
    String result = doc.read(jsonPath, String.class);
    if (result == null) {
      // might be an indefinite query, so retry as a list
      final List<Object> results = doc.read(jsonPath);
      if (!results.isEmpty()) {
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
