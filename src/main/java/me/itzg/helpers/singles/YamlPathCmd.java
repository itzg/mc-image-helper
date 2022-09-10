package me.itzg.helpers.singles;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "yaml-path", description = "Extracts a path from a YAML file using json-path syntax")
public class YamlPathCmd implements Callable<Integer> {

  @Option(names = "--file", description = "A YAML file to query")
  File yamlFile;

  @Parameters(arity = "1", description = "A YAML/JSON path in to query. Leading root anchor, $, will be added if not present")
  String yamlPath;

  @Override
  public Integer call() throws Exception {
    YAMLMapper yamlMapper = new YAMLMapper();

    DocumentContext context = JsonPath.using(Configuration.builder()
        .jsonProvider(new JacksonJsonProvider(yamlMapper))
        .build()).parse(yamlFile);

    Object result = context.read(yamlPath.startsWith("$") ? yamlPath : "$" + yamlPath);

    System.out.println(result);

    return ExitCode.OK;
  }
}
