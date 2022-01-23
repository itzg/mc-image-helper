package me.itzg.helpers.assertcmd;

import picocli.CommandLine.Command;

@Command(name = "assert", description = "Provides assertion operators for verifying container setup",
  subcommands = {
    FileExists.class
  }
)
public class AssertCommand {
}
