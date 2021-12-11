package me.itzg.helpers.versions;

import java.util.concurrent.Callable;
import org.apache.maven.artifact.versioning.ComparableVersion;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "compare-versions",
    description = "Used for shell scripting, exits with success(0) when comparison is satisfied or 1 when not"
)
public class CompareVersionsCommand implements Callable<Integer> {
  @Parameters(index = "0")
  String leftVersion;

  @Parameters(index = "1")
  Comparison comparison;

  @Parameters(index = "2")
  String rightVersion;

  @Override
  public Integer call() throws Exception {
    final ComparableVersion lhs = new ComparableVersion(leftVersion);
    final ComparableVersion rhs = new ComparableVersion(rightVersion);

    if (comparison == Comparison.lt && lhs.compareTo(rhs) < 0) {
      return 0;
    }
    else {
      return 1;
    }
  }
}
