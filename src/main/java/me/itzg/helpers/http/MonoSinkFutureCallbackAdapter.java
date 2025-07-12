package me.itzg.helpers.http;

import org.apache.hc.core5.concurrent.FutureCallback;
import reactor.core.publisher.MonoSink;

class MonoSinkFutureCallbackAdapter<T> implements FutureCallback<T> {

    private final MonoSink<T> sink;

    public MonoSinkFutureCallbackAdapter(MonoSink<T> sink) {
        this.sink = sink;
    }

    @Override
    public void completed(T result) {
        sink.success(result);
    }

    @Override
    public void failed(Exception ex) {
        sink.error(ex);
    }

    @Override
    public void cancelled() {
        sink.success();
    }
}
