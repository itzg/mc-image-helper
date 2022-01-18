package me.itzg.helpers.versions;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@Command(name = "java-release", description = "Outputs the Java release number, such as 8, 11, 17")
public class JavaReleaseCommand implements Callable<Integer> {

  @Override
  public Integer call() throws Exception {
    final String versionStr = System.getProperty("java.version");

    final Pattern pattern = Pattern.compile("(\\d+)\\.(\\d+)");
    final Matcher m = pattern.matcher(versionStr);
    if (m.lookingAt()) {
      final int major = Integer.parseInt(m.group(1));
      final int minor = Integer.parseInt(m.group(2));
      if (major == 1) {
        System.out.println(minor);
      } else if (major >= 9) {
        System.out.println(major);
      }
      else {
        System.err.println("Unexpected version: "+versionStr);
        return ExitCode.SOFTWARE;
      }
    }
    else {
      System.err.println("Malformed version: "+versionStr);
      return ExitCode.SOFTWARE;
    }

    return ExitCode.OK;
  }
}
