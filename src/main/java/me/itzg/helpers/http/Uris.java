package me.itzg.helpers.http;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Uris {

  private static final Pattern PLACEHOLDERS = Pattern.compile("\\{.*?}");
  private static final Pattern URI_DETECT = Pattern.compile("^(http|https)://");

  public static String populate(String url, Object... values) {
    if (values.length == 0) {
      return url;
    }

    Matcher m = PLACEHOLDERS.matcher(url);
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (m.find() && i < values.length) {
      try {
        m.appendReplacement(sb, URLEncoder.encode(values[i].toString(), "utf-8"));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("Internal error", e);
      }
      ++i;
    }
    m.appendTail(sb);

    return sb.toString();
  }

  /**
   * @param values replaces {@code {...}} placeholders in {@code url}
   */
  public static URI populateToUri(String url, Object... values) {
    return URI.create(populate(url, values));
  }

  public static boolean isUri(String value) {
    final Matcher m = URI_DETECT.matcher(value);
    return m.lookingAt();
  }

  private Uris() {
  }

}
