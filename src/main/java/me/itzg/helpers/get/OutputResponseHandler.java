package me.itzg.helpers.get;

import java.nio.file.Path;
import java.util.List;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

interface OutputResponseHandler extends HttpClientResponseHandler<Path> {

  default void setExpectedContentTypes(List<String> contentTypes) {};
}
