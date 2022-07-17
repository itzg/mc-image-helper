package me.itzg.helpers.http;

import java.net.URI;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.ITypeConverter;

@Slf4j
public class LenientUriConverter implements ITypeConverter<URI> {

  /**
   * <pre>
   *   http://example.org/path/path?a=b
   *   ^                 ^         ^
   *   |                 |         \ query or fragment part
   *   |                 \ path part
   *   \ scheme, address, credentials
   * </pre>
   */
  @SuppressWarnings("JavadocLinkAsPlainText")
  final static Pattern URL_PATTERN = Pattern.compile("(.+?://.+?)(/+.*?)?([?#].+)?");
  /**
   * Used to iterate over path segments
   * <p>
   *   NOTE: <code>/+</code> normalizes any double slashes in the path
   * </p>
   */
  final static Pattern PATH_SEGMENTS_PATTERN = Pattern.compile("/+([^/]+)");

  @Override
  public URI convert(String s) throws Exception {
    // first see if it is already a legal URI and avoid re-encoding
    try {
      return URI.create(s);
    } catch (Exception e) {
      log.debug("Given uri={} was not legal, so processing further", s);
    }

    final Matcher m = URL_PATTERN.matcher(s);
    if (!m.matches()) {
      throw new IllegalArgumentException("Failed to parse url: " + s);
    }

    StringBuilder sb = new StringBuilder(m.group(1));
    final String pathsPart = m.group(2);
    if (pathsPart != null) {
      final Matcher pathsMatcher = PATH_SEGMENTS_PATTERN.matcher(pathsPart);
      while (pathsMatcher.find()) {
        final String content = pathsMatcher.group(1);
        sb.append("/")
            .append(URLEncoder.encode(content, "utf-8"));
      }
    }
    final String trailer = m.group(3);
    if (trailer != null) {
      sb.append(trailer);
    }

    return URI.create(sb.toString());
  }
}
