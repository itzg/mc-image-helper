package me.itzg.helpers.assertcmd;

import picocli.CommandLine.Command;

@Command(name = "assert", description = "Provides assertion operators for verifying container setup",
  subcommands = {
      FileExists.class,
      FileNotExists.class,
      JsonPathEquals.class,
      PropertyEquals.class,
  }
)
public class AssertCommand {
}
