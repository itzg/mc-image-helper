package me.itzg.helpers.assertcmd;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "fileExists")
class FileExists implements Callable<Integer> {

  @Parameters
  List<Path> paths;

  @Override
  public Integer call() throws Exception {
    boolean missing = false;

    if (paths != null) {
      for (Path path : paths) {
        if (!Files.exists(path)) {
          System.err.printf("%s does not exist%n", path);
          missing = true;
        }
      }
    }

    return missing ? ExitCode.SOFTWARE : ExitCode.OK;
  }
}
