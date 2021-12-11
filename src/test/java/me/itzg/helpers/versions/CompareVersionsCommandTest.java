package me.itzg.helpers.versions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import picocli.CommandLine;

class CompareVersionsCommandTest {

  @Test
  void noArgs() {
    final int status =
        new CommandLine(new CompareVersionsCommand())
            .execute();

    assertThat(status).isEqualTo(2);
  }

  @ParameterizedTest
  @CsvSource({
      "1.18,   lt, 1.18.1, 0",
      "1.18.1, lt, 1.18.1, 1",
      "1.12.1, lt, 1.12.2, 0",
      "b1.7.3, lt, 1.18, 0",
      "1.18.1-rc3, lt, 1.18.1, 0",
      "21w44a, lt, 1.18.1, 1",
  })
  void combinations(String left, String op, String right, int expected) {
    final int status =
        new CommandLine(new CompareVersionsCommand())
            .execute(
                left, op, right
            );

    assertThat(status).isEqualTo(expected);
  }
}