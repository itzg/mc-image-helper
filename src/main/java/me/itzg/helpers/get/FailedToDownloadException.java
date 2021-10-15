package me.itzg.helpers.get;

import java.net.URI;
import org.apache.hc.client5.http.HttpResponseException;

public class FailedToDownloadException extends RuntimeException {

  private final URI uri;

  public FailedToDownloadException(URI uri, HttpResponseException e) {
    super(String.format("Failed to download %s: %s", uri, e.getMessage()), e);
    this.uri = uri;
  }

  public URI getUri() {
    return uri;
  }
}
