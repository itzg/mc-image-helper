package me.itzg.helpers.get;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.util.Arrays;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.TimeValue;

public class ExtendedRequestRetryStrategy extends DefaultHttpRequestRetryStrategy {

  public ExtendedRequestRetryStrategy(int maxRetries,
      int retryDelay) {
    super(maxRetries, TimeValue.ofSeconds(retryDelay),
        // most of this comes from
        // org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy.DefaultHttpRequestRetryStrategy(int, org.apache.hc.core5.util.TimeValue)
        Arrays.asList(
            InterruptedIOException.class,
            UnknownHostException.class,
            ConnectException.class,
            ConnectionClosedException.class,
            NoRouteToHostException.class,
            SSLException.class),
        Arrays.asList(
            HttpStatus.SC_TOO_MANY_REQUESTS,
            HttpStatus.SC_SERVICE_UNAVAILABLE,
            // some APIs, such as CurseForge, are intermittently responding 403
            HttpStatus.SC_FORBIDDEN)
        );
  }
}
