package me.itzg.helpers.assertcmd;

import java.util.List;
import java.util.concurrent.Callable;
import me.itzg.helpers.assertcmd.EvalExistence.MatchingPaths;
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
        final MatchingPaths matchingPaths = EvalExistence.matchingPaths(path);
        if (!matchingPaths.paths.isEmpty()) {
          if (matchingPaths.globbing) {
            System.err.printf("The files %s exist looking at %s%n", matchingPaths.paths, path);
          }
          else {
            System.err.printf("%s exists%n", path);
          }
          failed = true;
        }
      }
    }

    return failed ? ExitCode.SOFTWARE : ExitCode.OK;
  }

}
