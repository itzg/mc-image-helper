package me.itzg.helpers.get;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import picocli.CommandLine;
import picocli.CommandLine.Spec;

@CommandLine.Command(name = "get", description = "Download a file")
public class GetCommand implements Callable<Integer> {
    @Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    boolean showHelp;

    @CommandLine.Option(names = "-o",
    description = "Specifies the name of a file to write the downloaded content."
        + " If not provided, then content will be output to standard out.")
    File outputFile;

    @CommandLine.Parameters(arity = "1")
    URI uri;

    @Override
    public Integer call() throws Exception {
        final Response response = Executor.newInstance()
            .execute(
                Request.get(uri)
            );

        if (outputFile != null) {
            response.saveContent(outputFile);
        } else {
            final Content content = response.returnContent();
            spec.commandLine().getOut().print(
                content
                    .asString(StandardCharsets.UTF_8)
            );
        }

        return 0;
    }
}
