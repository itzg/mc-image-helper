package me.itzg.helpers.assertcmd;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "jsonPathEquals")
public class JsonPathEquals implements Callable<Integer> {
  @Option(names = "--file", required = true)
  Path file;

  @Option(names = "--path", required = true)
  String jsonPath;

  @Option(names = "--expect", required = true)
  String expectedValue;

  @Override
  public Integer call() throws Exception {
    if (!Files.exists(file)) {
      System.err.printf("The file %s does not exist%n", file);
      return ExitCode.SOFTWARE;
    }

    final DocumentContext doc = JsonPath.parse(file.toFile());

    final String result;
    try {
      result = doc.read(jsonPath, String.class);
    } catch (PathNotFoundException e) {
      System.err.printf("The path %s in %s does not exist%n",
          jsonPath, file);
      return ExitCode.SOFTWARE;
    }
    if (!Objects.equals(result, expectedValue)) {
      System.err.printf("Expected '%s' at the path %s in %s, but was '%s'%n",
          expectedValue, jsonPath, file, result);
      return ExitCode.SOFTWARE;
    }

    return ExitCode.OK;
  }
}
