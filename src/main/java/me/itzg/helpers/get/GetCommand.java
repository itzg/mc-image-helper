package me.itzg.helpers.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    @Option(names = "--log-progress-each", description = "Output a log as each URI is being retrieved")
    boolean logProgressEach;

    @Option(names = "--json-path",
        description = "Extract and output a JsonPath from the response")
    String jsonPath;

    @Option(names = {"-o", "--output"},
        description = "Specifies the name of a file or directory to write the downloaded content."
            + " If a directory is provided, the filename will be derived from the content disposition or the URI's path."
            + " If not provided, then content will be output to standard out.",
        paramLabel = "FILE|DIR")
    Path outputFile;

    @Parameters(arity = "1..", split = ",", paramLabel = "URI",
        description = "The URI of the resource to retrieve. When the output is a directory,"
        + " more than one URI can be requested.")
    List<URI> uris;

    @Override
    public Integer call() {
        final LatchingUrisInterceptor interceptor = new LatchingUrisInterceptor();

        try (CloseableHttpClient client = HttpClients.custom()
            .addExecInterceptorFirst("latchRequestUris", interceptor)
            .build()) {

            final PrintWriter stdout = spec.commandLine().getOut();

            if (jsonPath != null) {
                assertSingleUri();
                processSingleUri(uris.get(0), client, stdout, new JsonPathOutputHandler(stdout, jsonPath));
            }
            else if (outputFile == null) {
                assertSingleUri();
                processSingleUri(uris.get(0), client, stdout, new PrintWriterHandler(stdout));
            } else if (Files.isDirectory(outputFile)) {
                processUris(uris, client, stdout,
                    interceptor::reset, new OutputToDirectoryHandler(outputFile, interceptor));
            } else {
                assertSingleUri();
                processSingleUri(uris.get(0), client, stdout, new SaveToFileHandler(outputFile));
            }

        } catch (IllegalArgumentException e) {
            log.error("Invalid usage: {}", e.getMessage());
            log.debug("Details", e);
            return ExitCode.USAGE;
        } catch (Exception e) {
            log.error("Failed to download: {}", e.getMessage());
            log.debug("Details", e);
            return ExitCode.SOFTWARE;
        }

        return ExitCode.OK;
    }

    private void processUris(List<URI> uris, CloseableHttpClient client, PrintWriter stdout,
        Runnable reset, HttpClientResponseHandler<String> handler) throws URISyntaxException, IOException {
        for (URI uri : uris) {
            processSingleUri(uri, client, stdout, handler);
            reset.run();
        }
    }

    private void processSingleUri(URI uri, CloseableHttpClient client, PrintWriter stdout,
        HttpClientResponseHandler<String> handler) throws URISyntaxException, IOException {
        final URI requestUri = uri.getPath().startsWith("//") ?
            alterUriPath(uri, uri.getPath().substring(1)) : uri;

        if (logProgressEach) {
            log.info("Getting {}", requestUri);
        }
        else {
            log.debug("GETing uri={}", requestUri);
        }

        final HttpGet request = new HttpGet(requestUri);

        final String filename = client.execute(request, handler);
        if (outputFilename) {
            stdout.println(filename);
        }
    }

    private void assertSingleUri() {
        if (uris.size() > 1) {
            log.debug("Too many URIs: {}", uris);
            throw new IllegalArgumentException(
                "Multiple URIs can only be used with an output directory");
        }
    }

    private static URI alterUriPath(URI uri, String path) throws URISyntaxException {
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
