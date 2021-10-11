package me.itzg.helpers.get;

import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@CommandLine.Command(name = "get", description = "Download a file")
@Slf4j
public class GetCommand implements Callable<Integer> {
    @Spec
    CommandLine.Model.CommandSpec spec;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    boolean showHelp;

    @Option(names = "--output-filename", description = "Output the resulting filename")
    boolean outputFilename;

    @Option(names = "--json-path",
        description = "Extract and output a JsonPath from the response")
    String jsonPath;

    @Option(names = "-o",
        description = "Specifies the name of a file to write the downloaded content."
            + " If not provided, then content will be output to standard out.")
    Path outputFile;

    @Parameters(arity = "1")
    URI uri;

    @Override
    public Integer call() throws Exception {
        final LatchingUrisInterceptor interceptor = new LatchingUrisInterceptor();

        try (CloseableHttpClient client = HttpClients.custom()
            .addExecInterceptorFirst("latchRequestUris", interceptor)
            .build()) {

            final URI requestUri = this.uri.getPath().startsWith("//") ?
                alterUriPath(this.uri.getPath().substring(1)) : this.uri;

            log.debug("GETing uri={}", requestUri);

            final HttpGet request = new HttpGet(requestUri);

            final PrintWriter stdout = spec.commandLine().getOut();

            final HttpClientResponseHandler<String> handler;
            if (jsonPath != null) {
                handler = new JsonPathOutputHandler(stdout, jsonPath);
            }
            else if (outputFile == null) {
                handler = new PrintWriterHandler(stdout);
            } else if (Files.isDirectory(outputFile)) {
                handler = new OutputToDirectoryHandler(outputFile, interceptor);
            } else {
                handler = new SaveToFileHandler(outputFile);
            }

            final String filename = client.execute(request, handler);
            if (outputFilename) {
                stdout.println(filename);
            }
        } catch (Exception e) {
            log.error("Failed to download: {}", e.getMessage());
            log.debug("Details", e);
            return ExitCode.SOFTWARE;
        }

        return ExitCode.OK;
    }

    private URI alterUriPath(String path) throws URISyntaxException {
        return new URI(
            uri.getScheme(),
            uri.getAuthority(),
            uri.getHost(),
            uri.getPort(),
            path,
            uri.getQuery(),
            uri.getFragment()
        );
    }

}
