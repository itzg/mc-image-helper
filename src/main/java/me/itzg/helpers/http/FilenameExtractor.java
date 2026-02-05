package me.itzg.helpers.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class FilenameExtractor {
    // Examples:
    // attachment; filename="=?UTF-8?Q?Geyser-Spigot.jar?="; filename*=UTF-8''Geyser-Spigot.jar

    private static final Pattern RFC_2047_ENCODED = Pattern.compile("=\\?UTF-8\\?Q\\?(.+)\\?=");
    private static final Pattern RFC_5987_ENCODED = Pattern.compile("(UTF-8|ISO-8859-1)''(.+)");

    private final LatchingUrisInterceptor interceptor;

    public FilenameExtractor(LatchingUrisInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor is required");
    }

    static @Nullable String filenameFromContentDisposition(String headerValue) {
        if (headerValue == null) {
            return null;
        }

        final ContentType parsed = ContentType.parse(headerValue);
        log.debug("Response has contentDisposition={}", headerValue);
        final String filenameStar = parsed.getParameter("filename*");
        final String filename = parsed.getParameter("filename");
        if (filenameStar != null) {
            final Matcher m = RFC_5987_ENCODED.matcher(filenameStar);
            if (m.matches()) {
                try {
                    return decodeRfc5987(m.group(2), m.group(1));
                } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                    log.warn("Failed to decode filename* from {}", headerValue);
                    return null;
                }
            }
            else {
                return filenameStar;
            }
        }
        else if (filename != null) {
            final Matcher m = RFC_2047_ENCODED.matcher(filename);
            if (m.matches()) {
                return m.group(1);
            }
            else {
                return filename;
            }
        }
        else {
            log.debug("Unable to determine filename from header: {}", headerValue);
            return null;
        }
    }

    public String extract(ClassicHttpResponse response) throws IOException, ProtocolException {
        // Same as AbstractHttpClientResponseHandler
        if (response.getCode() >= HttpStatus.SC_REDIRECTION) {
            EntityUtils.consume(response.getEntity());
            throw new HttpResponseException(response.getCode(), response.getReasonPhrase());
        }

        final Header contentDisposition = response
            .getHeader("content-disposition");

        String filename = null;
        if (contentDisposition != null) {
            filename = filenameFromContentDisposition(contentDisposition.getValue());
        }
        if (filename == null) {
            final String path = interceptor.getLastRequestedUri().getPath();
            log.debug("Deriving filename from response path={}", path);
            final int pos = path.lastIndexOf('/');
            filename = path.substring(pos >= 0 ? pos + 1 : 0);
        }

        return filename;
    }


    // Rfc 5987 decoder for "%" bytes.
    // Referenced original source below, removes cxf lib dep.
    // Source: https://github.com/apache/cxf/blob/main/core/src/main/java/org/apache/cxf/attachment/Rfc5987Util.java#L73
    static String decodeRfc5987(String encoded, String charsetName) {
        final Charset charset = Charset.forName(charsetName);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream(); // decoded
        final int len = encoded.length();
        int i = 0;
        while (i < len) {
            char c = encoded.charAt(i);
            if (c == '%' && i + 2 < len) {
                int high = Character.digit(encoded.charAt(i + 1), 16);
                int low = Character.digit(encoded.charAt(i + 2), 16);
                if (high != -1 && low != -1) {
                    baos.write((high << 4) + low); // reconstruct byte
                    i += 3;
                    continue;
                }
            }
            baos.write(c);
            i++;
        }
        return new String(baos.toByteArray(), charset); // back to string
    }
}