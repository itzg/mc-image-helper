package me.itzg.helpers.get;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Spec;

@CommandLine.Command(name = "get", description = "Download a file")
@Slf4j
public class GetCommand implements Callable<Integer> {
    @Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this usage and exit")
    boolean showHelp;

    @CommandLine.Option(names = "-o",
        description = "Specifies the name of a file to write the downloaded content."
            + " If not provided, then content will be output to standard out.")
    Path outputFile;

    @CommandLine.Parameters(arity = "1")
    URI uri;

    @Override
    public Integer call() throws Exception {
        final LatchingUrisInterceptor interceptor = new LatchingUrisInterceptor();

        try (CloseableHttpClient client = HttpClients.custom()
            .addExecInterceptorFirst("latchRequestUris", interceptor)
            .build()) {

            processResponse(interceptor,
                Executor.newInstance(client)
                    .execute(Request.get(
                        uri.getPath().startsWith("//") ?
                            alterUriPath(uri.getPath().substring(1)) : uri
                    ))
            );
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

    private void processResponse(LatchingUrisInterceptor interceptor, Response response)
        throws IOException {
        if (outputFile != null) {
            if (Files.isDirectory(outputFile)) {

                // Derive filename and write to that file in the given directory
                response.handleResponse(httpResponse -> {
                    final Header contentDisposition = httpResponse
                        .getHeader("content-disposition");

                    String filename = null;
                    if (contentDisposition != null) {
                        final ContentType parsed = ContentType.parse(contentDisposition.getValue());
                        filename = parsed.getParameter("filename");
                    }
                    if (filename == null) {
                        final String path = interceptor.getUris().peekFirst().toString();
                        final int pos = path.lastIndexOf('/');
                        filename = path.substring(pos >= 0 ? pos + 1 : 0);
                    }

                    try (OutputStream out = Files.newOutputStream(
                        outputFile.resolve(filename))) {
                        httpResponse.getEntity().writeTo(out);
                    }
                    return null;
                });

            } else {
                response.saveContent(outputFile.toFile());
            }
        } else {
            final Content content = response.returnContent();
            spec.commandLine().getOut().print(
                content
                    .asString(StandardCharsets.UTF_8)
            );
        }
    }
}
