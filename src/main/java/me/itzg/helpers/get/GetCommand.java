package me.itzg.helpers.get;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
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

    @SuppressWarnings("unused")
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

    @Option(names = "--exists",
        description = "Test if the given URIs are retrievable"
    )
    boolean checkExists;

    @Option(names = "--accept",
        description = "Specifies the accept header to use with the request"
    )
    String acceptHeader;

    @Option(names = "--uris-file",
        description = "A file that contains a URL per line"
    )
    Path urisFile;

    @Parameters(split = ",", paramLabel = "URI",
        description = "The URI of the resource to retrieve. When the output is a directory,"
            + " more than one URI can be requested.",
        converter = LenientUriConverter.class
    )
    List<URI> uris;

    @Override
    public Integer call() {
        if (urisFile != null) {
            try {
                readUris();
            } catch (IOException e) {
                log.error("Failed to read URIs file: {}", e.getMessage());
                log.debug("Details", e);
                return ExitCode.SOFTWARE;
            }
        }
        if (uris == null || uris.isEmpty()) {
            throw new ParameterException(spec.commandLine(), "No URIs were given");
        }

        final LatchingUrisInterceptor interceptor = new LatchingUrisInterceptor();

        try (CloseableHttpClient client = HttpClients.custom()
            .addExecInterceptorFirst("latchRequestUris", interceptor)
            .build()) {

            final PrintWriter stdout = spec.commandLine().getOut();

            if (checkExists) {
                return checkUrisExist(client);
            } else if (jsonPath != null) {
                validateSingleUri();
                if (acceptHeader == null) {
                    acceptHeader = ContentType.APPLICATION_JSON.getMimeType();
                }
                processSingleUri(uris.get(0), client, stdout,
                    new JsonPathOutputHandler(stdout, jsonPath));
            } else if (outputFile == null) {
                validateSingleUri();
                processSingleUri(uris.get(0), client, stdout, new PrintWriterHandler(stdout));
            } else if (Files.isDirectory(outputFile)) {
                final Collection<Path> files = processUrisForDirectory(client, stdout,
                    interceptor);
                if (outputFilename) {
                    files.forEach(stdout::println);
                }
            } else {
                validateSingleUri();
                if (skipExisting && Files.isRegularFile(outputFile)) {
                    log.debug("Skipping uri={} since output file={} already exists", uris.get(0),
                        outputFile);
                    if (outputFilename) {
                        stdout.println(outputFile);
                    }
                } else {
                    final Path file = processSingleUri(uris.get(0), client, stdout,
                        new SaveToFileHandler(outputFile));
                    if (outputFilename) {
                        stdout.println(file);
                    }
                }
            }
        } catch (ParameterException e) {
            throw e;
        } catch (Exception e) {
            log.error("Operation failed: {}",
                e.getMessage() != null ? e.getMessage() : e.getClass());
            log.debug("Details", e);
            return ExitCode.SOFTWARE;
        }

        return ExitCode.OK;
    }

    private int checkUrisExist(CloseableHttpClient client) {
        if (uris.stream()
            .allMatch(uri -> {
                log.debug("Checking {}", uri);
                final HttpHead request = new HttpHead(uri);
                if (acceptHeader != null) {
                    request.addHeader(HttpHeaders.ACCEPT, acceptHeader);
                }

                try {
                    final int statusCode = client.execute(request).getCode();
                    if (statusCode == HttpStatus.SC_OK) {
                        return true;
                    } else {
                        log.warn("{} cannot be retrieved: status={}", uri, statusCode);
                        return false;
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(String.format("Failed to retrieve: %s", uri),
                        e);
                }
            })
        ) {
            return ExitCode.OK;
        } else {
            return ExitCode.SOFTWARE;
        }
    }

    private void readUris() throws IOException {
        if (uris == null) {
            uris = new ArrayList<>();
        }

        final LenientUriConverter uriConverter = new LenientUriConverter();

        Files.readAllLines(urisFile).stream()
            .filter(line -> !line.startsWith("#"))
            .filter(line -> !line.trim().isEmpty())
            .map(line -> {
                try {
                    return uriConverter.convert(line);
                } catch (URISyntaxException e) {
                    throw new ParameterException(spec.commandLine(),
                        String.format("%s is not a valid URI", line));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            })
            .forEach(uris::add);
    }

    private Collection<Path> processUrisForDirectory(CloseableHttpClient client,
        PrintWriter stdout,
        LatchingUrisInterceptor interceptor) throws URISyntaxException, IOException {

        if (usingPrune()) {
            if (pruneDepth <= 0) {
                throw new ParameterException(spec.commandLine(),
                    "Prune depth must be 1 or greater");
            }
        }

        final Set<Path> processed = new HashSet<>();

        for (URI uri : uris) {
            if (needsDownload(client, interceptor, uri, processed)) {
                processed.add(
                    processSingleUri(uri, client, stdout,
                        new OutputToDirectoryHandler(outputFile, interceptor))
                );
                interceptor.reset();
            }
        }

        if (usingPrune()) {
            pruneOtherFiles(processed);
        }

        return processed;
    }

    private boolean needsDownload(CloseableHttpClient client, LatchingUrisInterceptor interceptor,
        URI uri, Set<Path> processed) throws URISyntaxException, IOException {
        if (skipExisting) {
            final HttpHead headRequest = new HttpHead(uri.getPath().startsWith("//") ?
                alterUriPath(uri, uri.getPath().substring(1)) : uri);

            log.debug("Sending HEAD request to uri={}", uri);
            final String filename;
            try {
                filename = client.execute(headRequest,
                    new DeriveFilenameHandler(interceptor));
            } catch (HttpResponseException e) {
                throw new FailedToDownloadException(uri, e);
            }
            interceptor.reset();

            final Path resolvedFilename = outputFile.resolve(filename);
            if (Files.isRegularFile(resolvedFilename)) {
                if (logProgressEach) {
                    log.info("Skipping {} since {} already exists", uri, resolvedFilename);
                } else {
                    log.debug("Skipping {} since {} already exists", uri, resolvedFilename);
                }
                processed.add(resolvedFilename);
                return false;
            }
        }
        return true;
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
        if (acceptHeader != null) {
            request.addHeader(HttpHeaders.ACCEPT, acceptHeader);
        }

        final Path file;
        try {
            file = client.execute(request, handler);
        } catch (HttpResponseException e) {
            throw new FailedToDownloadException(uri, e);
        }
        if (logProgressEach) {
            if (file != null) {
                log.info("Downloaded {} to {}", uri, file);
            } else {
                log.info("Skipped {} since file already exists", uri);
            }
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
