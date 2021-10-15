package me.itzg.helpers.get;

import java.nio.file.Path;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

interface OutputResponseHandler extends HttpClientResponseHandler<Path> {

}
