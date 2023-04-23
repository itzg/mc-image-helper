package me.itzg.helpers.http;

import reactor.netty.http.client.HttpClient;

@FunctionalInterface
public interface RequestAssembler {
    HttpClient.ResponseReceiver<?> assembleRequest(HttpClient client);
}
