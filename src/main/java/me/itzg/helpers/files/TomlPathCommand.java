package me.itzg.helpers.files;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.ParseContext;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import java.io.File;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "toml-path", description = "Extracts a path from a TOML file using json-path syntax")
public class TomlPathCommand implements Callable<Integer> {

    @Option(names = "--file", paramLabel = "FILE", description = "A TOML file to query. If not set, reads stdin")
    File tomlFile;

    @Parameters(arity = "1",
        paramLabel = "query",
        description = "JSON path expression where root element $ can be omitted")
    String query;

    @Override
    public Integer call() throws Exception {

        final ParseContext parseContext = JsonPath.using(
            Configuration.builder()
                .jsonProvider(new JacksonJsonProvider(new TomlMapper()))
                .build()
        );

        final DocumentContext context;
        if (tomlFile != null) {
            context = parseContext.parse(tomlFile);
        }
        else {
            context = parseContext.parse(System.in);
        }

        final Object result = context.read(
            // if user left off root element reference, then add it
            // maybe using a shell where $ triggers interpolation
            query.startsWith("$") ? query : "$" + query
        );

        System.out.println(result);

        return ExitCode.OK;
    }

}
