package me.itzg.helpers.assertcmd;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
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
    //scanner.setBasedir(".");
    scanner.setCaseSensitive(false);

    if (paths != null) {
      scanner.setIncludes(paths);
      scanner.scan();
      String[] files = scanner.getIncludedFiles();
      if (files.length < paths.length) {
        System.err.printf("%s does not exist%n", Arrays.toString(paths));
        missing = true;
      }
    }

    return missing ? ExitCode.SOFTWARE : ExitCode.OK;
  }
}
