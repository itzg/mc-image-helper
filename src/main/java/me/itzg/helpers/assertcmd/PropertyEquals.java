package me.itzg.helpers.assertcmd;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "propertyEquals")
public class PropertyEquals implements Callable<Integer> {
  @Option(names = "--file", description = "Property file", required = true)
  Path file;

  @Option(names = "--property", required = true)
  String property;

  @Option(names = "--expect", required = true)
  String expectedValue;

  @Override
  public Integer call() throws Exception {
    if (!Files.exists(file)) {
      System.err.printf("The file %s does not exist%n", file);
      return ExitCode.SOFTWARE;
    }

    final Properties properties = new Properties();
    try (BufferedReader reader = Files.newBufferedReader(file)) {
      properties.load(reader);
    }

    final String value = properties.getProperty(this.property);
    if (value == null) {
      System.err.printf("The property %s in the file %s does not exist%n", property, file);
      return ExitCode.SOFTWARE;
    }

    if (!Objects.equals(value, expectedValue)) {
      System.err.printf("Expected the property %s in the file %s to be '%s', but was '%s'%n",
          property, file, expectedValue, value);
      return ExitCode.SOFTWARE;
    }

    return ExitCode.OK;
  }
}
