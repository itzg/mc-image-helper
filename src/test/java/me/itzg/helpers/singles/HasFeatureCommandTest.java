package me.itzg.helpers.singles;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class HasFeatureCommandTest {

  @Test
  void subcommandExists() {
    final int exitCode = new CommandLine(new me.itzg.helpers.McImageHelper())
        .execute("has-feature", "hash");
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void subcommandDoesNotExist() {
    final int exitCode = new CommandLine(new me.itzg.helpers.McImageHelper())
        .execute("has-feature", "nonexistent");
    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void subcommandExistsWithExistingOption() {
    final int exitCode = new CommandLine(new me.itzg.helpers.McImageHelper())
        .execute("has-feature", "install-fabric-loader", "help");
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void subcommandExistsWithNonExistingOption() {
    final int exitCode = new CommandLine(new me.itzg.helpers.McImageHelper())
        .execute("has-feature", "install-fabric-loader", "nonexistent");
    assertThat(exitCode).isEqualTo(1);
  }

  @Test
  void subcommandExistsWithMultipleExistingOptions() {
    final int exitCode = new CommandLine(new me.itzg.helpers.McImageHelper())
        .execute("has-feature", "install-fabric-loader", "help", "loader-version");
    assertThat(exitCode).isEqualTo(0);
  }

  @Test
  void subcommandExistsWithMultipleOptionsOneMissing() {
    final int exitCode = new CommandLine(new me.itzg.helpers.McImageHelper())
        .execute("has-feature", "install-fabric-loader", "help", "nonexistent");
    assertThat(exitCode).isEqualTo(1);
  }
}
