package me.itzg.helpers.get;

import java.net.URI;
import me.itzg.helpers.errors.ExitCodeProvider;
import org.apache.hc.client5.http.HttpResponseException;

public class RequestFailedException extends RuntimeException implements ExitCodeProvider {

  private final int statusCode;

  public RequestFailedException(URI uri, HttpResponseException e) {
    super(String.format("Failed to download %s: %s", uri, e.getMessage()), e);
    this.statusCode = e.getStatusCode();
  }

  @Override
  public int exitCode() {
    switch (statusCode) {
      case 404:
        return 44;
      case 429:
        return 42;
      default:
        return statusCode / 100;
    }
  }
}
