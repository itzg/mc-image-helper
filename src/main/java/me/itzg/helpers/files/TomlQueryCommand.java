package me.itzg.helpers.files;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.toml.TomlFactory;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "toml-query")
public class TomlQueryCommand implements Callable<Integer> {

    public static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    @Parameters(index = "0", arity = "1",
        paramLabel = "query",
        description = "JSON path expression where root element $ can be omitted")
    String query;

    @Parameters(index = "1", arity = "0..1",
        paramLabel = "file",
        description = "TOML file or reads stdin")
    Path path;

    @Override
    public Integer call() throws Exception {
        final Map<String,Object> content;
        try (JsonParser parser = loadParser()) {
            if (path != null) {
                content = new TomlMapper().readValue(parser, MAP_TYPE);
            }
            else {
                content = new TomlMapper().readValue(parser, MAP_TYPE);
            }
        }
        final Object result = JsonPath.read(content,
            // if user left off root element reference, then add it
            // maybe using a shell where $ triggers interpolation
            query.startsWith("$") ? query : "$" + query
        );

        System.out.println(result);

        return ExitCode.OK;
    }

    private JsonParser loadParser() throws IOException {
        final JsonParser parser;
        if (path != null) {
            parser = new TomlFactory().createParser(path.toFile());
        }
        else {
            parser = new TomlFactory().createParser(System.in);
        }
        return parser;
    }
}
