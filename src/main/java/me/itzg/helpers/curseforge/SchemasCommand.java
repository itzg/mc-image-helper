package me.itzg.helpers.curseforge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import java.util.concurrent.Callable;
import me.itzg.helpers.json.ObjectMappers;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@Command(name = "schemas", description = "Output relevant JSON schemas")
public class SchemasCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

        final JsonSchemaGenerator schemaGen = new JsonSchemaGenerator(
            objectMapper
        );

        final JsonNode schema = schemaGen.generateJsonSchema(ExcludeIncludesContent.class);
        System.out.println(objectMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(schema));

        return ExitCode.OK;
    }
}
