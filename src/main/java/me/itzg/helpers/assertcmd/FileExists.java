package me.itzg.helpers.assertcmd;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;
import org.apache.tools.ant.DirectoryScanner;

@Command(name = "fileExists")
class FileExists implements Callable<Integer> {

  @Parameters
  String[] paths;

  @Override
  public Integer call() throws Exception {
    boolean missing = false;
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setCaseSensitive(false);

    if (paths != null) {
      for (String path : paths) {
        scanner.setIncludes(new String[] { path });
        scanner.scan();
        String[] files = scanner.getIncludedFiles();
        if (files.length < 1) {
          System.err.printf("%s does not exist%n", path);
          missing = true;
        }
      }
    }

    return missing ? ExitCode.SOFTWARE : ExitCode.OK;
  }
}
