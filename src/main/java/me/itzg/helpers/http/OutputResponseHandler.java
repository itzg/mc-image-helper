package me.itzg.helpers.http;

import java.nio.file.Path;
import java.util.List;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

public interface OutputResponseHandler extends HttpClientResponseHandler<Path> {

  default void setExpectedContentTypes(List<String> contentTypes) {}
}
