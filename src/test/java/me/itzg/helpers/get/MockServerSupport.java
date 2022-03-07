package me.itzg.helpers.get;

import static org.mockserver.model.HttpRequest.request;

import java.net.MalformedURLException;
import java.net.URL;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

class MockServerSupport {

  private ClientAndServer client;

  public MockServerSupport(ClientAndServer client) {

    this.client = client;
  }

  URL buildMockedUrl(String s) throws MalformedURLException {
    return new URL("http", "localhost", client.getLocalPort(), s);
  }

  @FunctionalInterface
  interface RequestCustomizer {
    HttpRequest customize(HttpRequest request);
  }

  void expectRequest(String method, String path, HttpResponse httpResponse) {
    expectRequest(method, path, request -> request, httpResponse);
  }

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
