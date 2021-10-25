package me.itzg.helpers.get;

import java.net.URI;
import okhttp3.HttpUrl;
import picocli.CommandLine.ITypeConverter;

public class LenientUriConverter implements ITypeConverter<URI> {

  @Override
  public URI convert(String s) throws Exception {
    final HttpUrl url = HttpUrl.parse(s);
    if (url == null) {
      throw new IllegalArgumentException("Failed to parse url: " + s);
    }
    return url.uri();
  }
}
