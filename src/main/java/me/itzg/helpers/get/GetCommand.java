package me.itzg.helpers.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
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

    @Option(names = "--skip-existing", description = "Do not retrieve if the output file already exists")
    boolean skipExisting;

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

    @Option(names = "--prune-others",
        description = "When set and using an output directory, files that match the given"
            + " glob patterns will be pruned if not part of the download set. For example *.jar",
        paramLabel = "GLOB",
        split = ","
    )
    List<String> pruneOthers;

    @Option(names = "--prune-depth",
        description = "When using prune-others, this specifies how deep to search for files to prune",
        defaultValue = "1"
    )
    int pruneDepth;

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
                validateSingleUri();
                processSingleUri(uris.get(0), client, stdout,
                    new JsonPathOutputHandler(stdout, jsonPath));
            } else if (outputFile == null) {
                validateSingleUri();
                processSingleUri(uris.get(0), client, stdout, new PrintWriterHandler(stdout));
            } else if (Files.isDirectory(outputFile)) {
                processUrisForDirectory(client, stdout,
                    interceptor);
            } else {
                validateSingleUri();
                if (skipExisting && Files.isRegularFile(outputFile)) {
                    log.debug("Skipping uri={} since output file={} already exists", uris.get(0),
                        outputFile);
                } else {
                    processSingleUri(uris.get(0), client, stdout,
                        new SaveToFileHandler(outputFile));
                }
            }
        } catch (ParameterException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download: {}",
                e.getMessage() != null ? e.getMessage() : e.getClass());
            log.debug("Details", e);
            return ExitCode.SOFTWARE;
        }

        return ExitCode.OK;
    }

    private void processUrisForDirectory(CloseableHttpClient client,
        PrintWriter stdout,
        LatchingUrisInterceptor interceptor) throws URISyntaxException, IOException {

        if (usingPrune()) {
            if (pruneDepth <= 0) {
                throw new ParameterException(spec.commandLine(),
                    "Prune depth must be 1 or greater");
            }
        }

        final Set<Path> outputs = new HashSet<>();

        for (URI uri : uris) {
            if (skipExisting) {
                final HttpHead headRequest = new HttpHead(uri.getPath().startsWith("//") ?
                    alterUriPath(uri, uri.getPath().substring(1)) : uri);

                log.debug("Sending HEAD request to uri={}", uri);
                final String filename = client.execute(headRequest,
                    new DeriveFilenameHandler(interceptor));
                interceptor.reset();

                final Path resolvedFilename = outputFile.resolve(filename);
                if (Files.isRegularFile(resolvedFilename)) {
                    if (logProgressEach) {
                        log.info("Skipping {} since {} already exists", uri, resolvedFilename);
                    } else {
                        log.debug("Skipping {} since {} already exists", uri, resolvedFilename);
                    }
                    continue;
                }
            }

            outputs.add(
                processSingleUri(uri, client, stdout,
                    new OutputToDirectoryHandler(outputFile, interceptor))
            );
            interceptor.reset();
        }

        if (usingPrune()) {
            pruneOtherFiles(outputs);
        }
    }

    private boolean usingPrune() {
        return pruneOthers != null && !pruneOthers.isEmpty();
    }

    private void pruneOtherFiles(Set<Path> outputs) throws IOException {
        final List<PathMatcher> pruneMatchers = pruneOthers.stream()
            .map(glob -> FileSystems.getDefault().getPathMatcher("glob:" + glob))
            .collect(Collectors.toList());

        try (Stream<Path> dirStream = Files.walk(outputFile, pruneDepth)) {
            dirStream
                .filter(Files::isRegularFile)
                // don't prune ones we processed
                .filter(path -> !outputs.contains(path))
                // match the given globs
                .filter(path -> pruneMatchers.stream()
                    .anyMatch(pathMatcher -> pathMatcher.matches(path.getFileName())))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        log.info("Pruned {}", path);
                    } catch (IOException e) {
                        log.warn("Failed to delete {}", path);
                    }
                });
        }
    }

    private Path processSingleUri(URI uri, CloseableHttpClient client, PrintWriter stdout,
        OutputResponseHandler handler) throws URISyntaxException, IOException {
        final URI requestUri = uri.getPath().startsWith("//") ?
            alterUriPath(uri, uri.getPath().substring(1)) : uri;

        log.debug("Getting uri={}", requestUri);

        final HttpGet request = new HttpGet(requestUri);

        final Path file = client.execute(request, handler);
        if (logProgressEach) {
            if (file != null) {
                log.info("Downloaded {} to {}", uri, file);
            } else {
                log.info("Skipped {} since file already exists", uri);
            }
        }
        if (outputFilename && file != null) {
            stdout.println(file);
        }

        return file;
    }

    private void validateSingleUri() {
        if (uris.size() > 1) {
            log.debug("Too many URIs: {}", uris);
            throw new ParameterException(spec.commandLine(),
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
