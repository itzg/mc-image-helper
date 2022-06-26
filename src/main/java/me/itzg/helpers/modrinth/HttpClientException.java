package me.itzg.helpers.modrinth;

import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.Getter;
import lombok.ToString;

@Getter @ToString
public class HttpClientException extends RuntimeException {

  private final HttpResponseStatus status;
  private final String body;

  public HttpClientException(HttpResponseStatus status, String body) {
    super("HttpClient request failed: " + status);
    this.status = status;
    this.body = body;
  }

  @SuppressWarnings("unused")
  public boolean isNotFound() {
    return status.code() == 404;
  }
}
