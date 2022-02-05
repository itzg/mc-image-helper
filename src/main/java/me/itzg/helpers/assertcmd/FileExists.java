package me.itzg.helpers.assertcmd;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "fileExists")
class FileExists implements Callable<Integer> {
  private static final Pattern globSymbols = Pattern.compile("[*?]|\\{.+?}");
  // matches everything up to the last path separator either / or \
  private static final Pattern pathSeparators = Pattern.compile(".*[/\\\\]");

  @Parameters
  List<String> paths;

  @Override
  public Integer call() throws Exception {
    boolean missing = false;

    if (paths != null) {
      for (String path : paths) {
        if (!exists(path)) {
          System.err.printf("%s does not exist%n", path);
          missing = true;
        }
      }
    }

    return missing ? ExitCode.SOFTWARE : ExitCode.OK;
  }

  private boolean exists(String pathSpec) throws IOException {
    final Matcher globMatcher = globSymbols.matcher(pathSpec);
    // find the first globbing symbol
    if (!globMatcher.find()) {
      // no globbing, just a specific path
      return Files.exists(Paths.get(pathSpec));
    }

    // find last path separator in the text before the glob
    final Matcher sepMatcher = pathSeparators.matcher(pathSpec.substring(0, globMatcher.start()));
    final Path walkStart;
    // ...by looking from the start of the string
    if (sepMatcher.lookingAt()) {
      // ...and grabbing the end of the matched text
      walkStart = Paths.get(pathSpec.substring(0, sepMatcher.end()));
    }
    else {
      // no separator, so process relative paths
      walkStart = Paths.get("");
    }

    final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(
        "glob:" +
            // escape any Windows backslashes
            pathSpec.replace("\\", "\\\\")
    );
    try (Stream<Path> pathStream = Files.walk(walkStart)) {
      return pathStream
          .filter(Files::isRegularFile)
          .anyMatch(pathMatcher::matches);
    }
  }

}
