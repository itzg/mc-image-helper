package me.itzg.helpers.assertcmd;

import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "fileNotExists")
class FileNotExists implements Callable<Integer> {

  @Parameters
  List<String> paths;

  @Override
  public Integer call() throws Exception {
    boolean failed = false;

    if (paths != null) {
      for (String path : paths) {
        if (EvalExistence.exists(path)) {
          System.err.printf("%s exists%n", path);
          failed = true;
        }
      }
    }

    return failed ? ExitCode.SOFTWARE : ExitCode.OK;
  }

}
