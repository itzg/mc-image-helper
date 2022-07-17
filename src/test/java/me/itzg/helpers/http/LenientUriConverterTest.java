package me.itzg.helpers.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class LenientUriConverterTest {

  @Test
  void leavesPlusAsIs() throws Exception {
    final URI result = new LenientUriConverter().convert(
        "https://media.forgecdn.net/files/3482/169/Valhelsia+3-3.4.4-SERVER.zip");

    // can't use URI#getPath() since it decodes away the %2B encoding of +
    assertThat(result.getRawPath()).isEqualTo("/files/3482/169/Valhelsia+3-3.4.4-SERVER.zip");
  }

  @Test
  void convertsSquareBrackets() throws Exception {
    final URI result = new LenientUriConverter().convert(
        "https://files.forgecdn.net/files/2320/259/[1.10.x]FenceOverhaul-1.2.1.jar");

    assertThat(result.getRawPath()).isEqualTo("/files/2320/259/%5B1.10.x%5DFenceOverhaul-1.2.1.jar");
  }

  @Test
  void leavesLegalUriAsIs() throws Exception {
    final URI result = new LenientUriConverter().convert(
        "https://cdn.modrinth.com/data/P7dR8mSH/versions/0.55.3+1.19/fabric-api-0.55.3%2B1.19.jar"
    );

    assertThat(result).isEqualTo(
        URI.create("https://cdn.modrinth.com/data/P7dR8mSH/versions/0.55.3+1.19/fabric-api-0.55.3%2B1.19.jar"));
  }
}