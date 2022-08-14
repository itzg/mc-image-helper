package me.itzg.helpers.http;

import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.AbstractHttpClientResponseHandler;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.slf4j.Logger;

@Slf4j
public abstract class LoggingResponseHandler<T> extends AbstractHttpClientResponseHandler<T> {

  static void logResponse(Logger log, ClassicHttpResponse response) {
    log.debug("Response: status={}, reason={}, headers={}",
        response.getCode(), response.getReasonPhrase(), response.getHeaders());
  }

  @Override
  public T handleResponse(ClassicHttpResponse response) throws IOException {
    logResponse(log, response);
    return super.handleResponse(response);
  }
}
