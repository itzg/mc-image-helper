package me.itzg.helpers.get;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import picocli.CommandLine.ITypeConverter;

public class LenientUriConverter implements ITypeConverter<URI> {

  @Override
  public URI convert(String s) throws Exception {
    final int filePartIndex = s.lastIndexOf('/');
    if (filePartIndex < 0) {
      throw new URISyntaxException(s, "No slashes present");
    }

    final String encoded = s.substring(0, filePartIndex+1) +
        URLEncoder.encode(s.substring(filePartIndex+1), StandardCharsets.UTF_8.toString());

    return new URI(encoded);
  }
}
