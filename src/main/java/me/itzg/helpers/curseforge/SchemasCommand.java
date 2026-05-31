package me.itzg.helpers.curseforge;

import java.util.concurrent.Callable;
import me.itzg.helpers.json.ObjectMappers;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;

@Command(name = "schemas", description = "Output relevant JSON schemas")
public class SchemasCommand implements Callable<Integer> {

    @Override
    public Integer call() throws Exception {
        ObjectMappers.outputSchema(ExcludeIncludesContent.class);

        return ExitCode.OK;
    }

}
