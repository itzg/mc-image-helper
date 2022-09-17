package me.itzg.helpers.get;

import static org.mockserver.model.HttpRequest.request;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class MockServerSupport {

  private final ClientAndServer client;

  public MockServerSupport(ClientAndServer client) {

    this.client = client;
  }

  URL buildMockedUrl(String path) throws MalformedURLException {
    return new URL("http", "localhost", client.getLocalPort(), path);
  }

  @SuppressWarnings("SameParameterValue")
  URL buildMockedUrl(String path, String userInfo) throws URISyntaxException, MalformedURLException {
    return new URI(
        "http",
        userInfo,
        "localhost",
        client.getLocalPort(),
        path,
        null,
        null
    ).toURL();
  }

  @FunctionalInterface
  interface RequestCustomizer {
    HttpRequest customize(HttpRequest request);
  }

  void expectRequest(String method, String path, HttpResponse httpResponse) {
    expectRequest(method, path, request -> request, httpResponse);
  }

  @SuppressWarnings("SameParameterValue")
  void expectRequest(String method, String path, HttpResponse httpResponse, int responseTimes) {
    expectRequest(method, path, request -> request, httpResponse, responseTimes);
  }

  void expectRequest(String method,
      String path, RequestCustomizer requestCustomizer,
      HttpResponse httpResponse) {
    expectRequest(method, path, requestCustomizer, httpResponse, 1);
  }

  void expectRequest(String method,
      String path, RequestCustomizer requestCustomizer,
      HttpResponse httpResponse, int responseTimes) {
    client
        .when(
            requestCustomizer.customize(
                request()
                    .withMethod(method)
                    .withPath(path)
            )
            ,
            Times.exactly(responseTimes)
        )
        .respond(
            httpResponse
        );
  }

}
