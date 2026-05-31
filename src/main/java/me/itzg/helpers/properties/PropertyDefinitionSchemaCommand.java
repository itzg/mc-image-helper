package me.itzg.helpers.properties;

import java.util.concurrent.Callable;
import me.itzg.helpers.json.ObjectMappers;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@Command(name = "schema",
    // so that parent arguments are not validated as normal
    helpCommand = true,
    description = "Output JSON schema for property definitions")
public class PropertyDefinitionSchemaCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        ObjectMappers.outputSchema(PropertyDefinition.class);
        return ExitCode.OK;
    }
}
