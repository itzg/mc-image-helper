package me.itzg.helpers.http;

import reactor.core.publisher.Mono;

public interface RequestResponseAssembler<T> {
    Mono<T> assemble();
}
