package me.itzg.helpers.http;

import java.net.URI;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.ExecChain;
import org.apache.hc.client5.http.classic.ExecChain.Scope;
import org.apache.hc.client5.http.classic.ExecChainHandler;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;

@Slf4j
public class LatchingUrisInterceptor implements ExecChainHandler {

  private final Deque<URI> uris = new ConcurrentLinkedDeque<>();

  @SneakyThrows
  @Override
  public ClassicHttpResponse execute(ClassicHttpRequest request, Scope scope, ExecChain chain) {
    log.debug("Intercepting request uri={}", request.getUri());

    uris.push(request.getUri());

    final ClassicHttpResponse resp = chain.proceed(request, scope);
    final RedirectLocations redirectLocations = scope.clientContext.getRedirectLocations();
    if (redirectLocations != null) {
      log.debug("Post-request capturing redirectLocations={}", redirectLocations.getAll());

      uris.addAll(redirectLocations.getAll());
    }
    return resp;
  }

  public URI getLastRequestedUri() {
    return uris.peekLast();
  }

  public void reset() {
    uris.clear();
  }
}
