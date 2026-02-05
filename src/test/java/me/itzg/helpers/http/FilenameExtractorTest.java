package me.itzg.helpers.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FilenameExtractorTest {

  @Test
  void decodeRfc5987PercentEncodedUtf8() {
    assertThat(FilenameExtractor.decodeRfc5987("Hello%20Overworld%21", "UTF-8"))
        .isEqualTo("Hello Overworld!");
  }
}
