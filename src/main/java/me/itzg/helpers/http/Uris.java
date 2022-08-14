package me.itzg.helpers.http;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Uris {

  final static Pattern PLACEHOLDERS = Pattern.compile("\\{.*?}");

  public static String populate(String url, String... values) {
    if (values.length == 0) {
      return url;
    }

    Matcher m = PLACEHOLDERS.matcher(url);
    StringBuffer sb = new StringBuffer();
    int i = 0;
    while (m.find() && i < values.length) {
      try {
        m.appendReplacement(sb, URLEncoder.encode(values[i], "utf-8"));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("Internal error", e);
      }
      ++i;
    }
    m.appendTail(sb);

    return sb.toString();
  }

  public static URI populateToUri(String url, String... values) {
    return URI.create(populate(url, values));
  }

  private Uris() {
  }
}
