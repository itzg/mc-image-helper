package me.itzg.helpers.get;

import static me.itzg.helpers.McImageHelper.OPTION_SPLIT_COMMAS;
import static me.itzg.helpers.http.Fetch.fetch;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.DeriveFilenameHandler;
import me.itzg.helpers.http.LatchingUrisInterceptor;
import me.itzg.helpers.http.LenientUriConverter;
import me.itzg.helpers.http.NotModifiedHandler;
import me.itzg.helpers.http.OutputResponseHandler;
import me.itzg.helpers.http.OutputToDirectoryHandler;
import org.apache.hc.client5.http.HttpResponseException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpResponse;
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

    @Option(names = {"-z", "--skip-up-to-date"},
        description = "Skips re-downloading a file that is up to date"
    )
    boolean skipUpToDate;

    @Option(names = "--log-progress-each", description = "Output a log as each URI is being retrieved")
    boolean logProgressEach;

    @Option(names = "--json-path",
        description = "Extract and output a JsonPath from the response")
    String jsonPath;

    @Option(names = "--json-value-when-missing",
        defaultValue = "null", description = "Defines the value that is output when the requested JSON path does not exist."
        + " An empty value results in a non-zero exit code.")
    String jsonValueWhenMissing;

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
        split = OPTION_SPLIT_COMMAS
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
        description = "Specifies the accepted content type(s) to use with the request",
        split = OPTION_SPLIT_COMMAS
    )
    List<String> acceptContentTypes;

    @Option(names = "--apikey",
        description = "Specifies the accept header to use with the request"
    )
    String apikeyHeader;

    @Option(names = "--uris-file",
        description = "A file that contains a URL per line"
    )
    Path urisFile;

    @Option(names = "--retry-count", defaultValue = "5")
    int retryCount;

    @Option(names = "--retry-delay", description = "in seconds", defaultValue = "2")
    int retryDelay;

    @Parameters(split = OPTION_SPLIT_COMMAS, paramLabel = "URI",
        description = "The URI of the resource to retrieve. When the output is a directory,"
            + " more than one URI can be requested.",
        converter = LenientUriConverter.class
    )
    List<URI> uris;

    private final static DateTimeFormatter httpDateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    @Override
    public Integer call() throws IOException {
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
            .useSystemProperties()
            .setUserAgent("mc-image-helper/"+ McImageHelper.getVersion()+" (cmd=get)")
            .setRetryStrategy(
                new ExtendedRequestRetryStrategy(retryCount, retryDelay)
            )
            .build()) {

            final PrintWriter stdout = spec.commandLine().getOut();

            if (checkExists) {
                return checkUrisExist(client);
            } else if (jsonPath != null) {
                validateSingleUri();

                final String jsonMimeType = ContentType.APPLICATION_JSON.getMimeType();
                if (acceptContentTypes == null) {
                    acceptContentTypes = Collections.singletonList(jsonMimeType);
                } else if (!acceptContentTypes.contains(jsonMimeType)) {
                    acceptContentTypes = new ArrayList<>(acceptContentTypes);
                    acceptContentTypes.add(jsonMimeType);
                }

                processSingleUri(uris.get(0), client,
                    null, new JsonPathOutputHandler(
                        stdout, jsonPath,
                        jsonValueWhenMissing != null && !jsonValueWhenMissing.isEmpty() ?
                            jsonValueWhenMissing : null
                    ));
            } else if (outputFile == null) {
                validateSingleUri();
                processSingleUri(uris.get(0), client, null, new PrintWriterHandler(stdout));
            } else if (Files.isDirectory(outputFile)) {
                final Collection<Path> files = processUrisForDirectory(client,
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
                    final Path file = fetch(uris.get(0))
                        .toFile(outputFile)
                        .skipUpToDate(skipUpToDate)
                        .acceptContentTypes(acceptContentTypes)
                        .handleDownloaded((uri, f, contentSizeBytes) -> {
                            if (logProgressEach) {
                                log.info("Downloaded {}", f);
                            }
                        })
                        .execute();
                    if (this.outputFilename) {
                        stdout.println(file);
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new InvalidParameterException("A given URI is invalid", e);
        }

        return ExitCode.OK;
    }

    private int checkUrisExist(CloseableHttpClient client) {
        if (uris.stream()
            .allMatch(uri -> {
                log.debug("Checking {}", uri);
                final HttpHead request = new HttpHead(uri);
                if (acceptContentTypes != null) {
                    for (String acceptContentType : acceptContentTypes) {
                        request.addHeader(HttpHeaders.ACCEPT, acceptContentType);
                    }
                }
                if (apikeyHeader != null) {
                    request.addHeader("x-api-key", apikeyHeader);
                }
                try {
                    final int statusCode = client.execute(request, HttpResponse::getCode);
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
        LatchingUrisInterceptor interceptor) throws URISyntaxException, IOException {

        if (usingPrune()) {
            if (pruneDepth <= 0) {
                throw new ParameterException(spec.commandLine(),
                    "Prune depth must be 1 or greater");
            }
        }

        final Set<Path> processed = new HashSet<>();

        for (URI uri : uris) {
            NeedsDownloadResult result = needsDownload(client, interceptor, uri, processed);
            if (result.needsDownload) {
                processed.add(
                    processSingleUri(uri, client,
                        skipUpToDate ? result.resolvedFilename : null,
                        new OutputToDirectoryHandler(outputFile, interceptor, logProgressEach))
                );
                interceptor.reset();
            }
        }

        if (usingPrune()) {
            pruneOtherFiles(processed);
        }

        return processed;
    }

    @RequiredArgsConstructor
    static class NeedsDownloadResult {
        final boolean needsDownload;
        final Path resolvedFilename;
    }
    private NeedsDownloadResult needsDownload(CloseableHttpClient client, LatchingUrisInterceptor interceptor,
        URI uri, Set<Path> processed) throws URISyntaxException, IOException {
        if (skipExisting || skipUpToDate) {
            final String filename = resolveExpectedFilename(client, interceptor, uri);
            interceptor.reset();

            final Path resolvedFilename = outputFile.resolve(filename);
            if (skipExisting && Files.isRegularFile(resolvedFilename)) {
                if (logProgressEach) {
                    log.info("Skipping {} since {} already exists", uri, resolvedFilename);
                } else {
                    log.debug("Skipping {} since {} already exists", uri, resolvedFilename);
                }
                processed.add(resolvedFilename);
                return new NeedsDownloadResult(false, null);
            }
            else {
                return new NeedsDownloadResult(true, resolvedFilename);
            }
        }
        return new NeedsDownloadResult(true, null);
    }

    private static String resolveExpectedFilename(CloseableHttpClient client, LatchingUrisInterceptor interceptor, URI uri)
        throws URISyntaxException, IOException {
        final HttpHead headRequest = new HttpHead(uri.getPath().startsWith("//") ?
            alterUriPath(uri, uri.getPath().substring(1)) : uri);

        log.debug("Sending HEAD request to uri={}", uri);
        try {
            return client.execute(headRequest,
                new DeriveFilenameHandler(interceptor)
            );
        } catch (HttpResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_METHOD_NOT_ALLOWED) {
                log.warn("Endpoint at {} does not allow HEAD request, so deriving from URI's path", uri);
                return Paths.get(uri.getPath()).getFileName().toString();
            }
            throw new RequestFailedException(uri, e);
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

    private Path processSingleUri(URI uri, CloseableHttpClient client,
        Path modifiedSinceFile, OutputResponseHandler handler) throws URISyntaxException, IOException {
        URI requestUri = uri.getPath().startsWith("//") ?
            alterUriPath(uri, uri.getPath().substring(1)) : uri;

        final String authHeader;
        if (requestUri.getUserInfo() != null) {
            authHeader = "Basic " +
                Base64.getEncoder().withoutPadding()
                    .encodeToString(requestUri.getUserInfo().getBytes(StandardCharsets.UTF_8));
            requestUri = removeUserInfo(requestUri);
        }
        else {
            authHeader = null;
        }

        log.debug("Getting uri={}", requestUri);

        final HttpGet request = new HttpGet(requestUri);
        if (authHeader != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
        if (acceptContentTypes != null) {
            for (String acceptContentType : acceptContentTypes) {
                request.addHeader(HttpHeaders.ACCEPT, acceptContentType);
            }
            handler.setExpectedContentTypes(acceptContentTypes);
        }
        if (apikeyHeader != null) {
            request.addHeader("x-api-key", apikeyHeader);
        }
        if (modifiedSinceFile != null && Files.exists(modifiedSinceFile)) {
            final FileTime lastModifiedTime = Files.getLastModifiedTime(modifiedSinceFile);
            request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                httpDateTimeFormatter.format(lastModifiedTime.toInstant())
            );

            // wrap the handler to intercept the NotModified response
            handler = new NotModifiedHandler(modifiedSinceFile, handler, logProgressEach);
        }

        final Path file;
        try {
            log.debug("Executing {} with headers={}", request, request.getHeaders());
            file = client.execute(request, handler);
        } catch (HttpResponseException e) {
            throw new RequestFailedException(uri, e);
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

    private static URI removeUserInfo(URI uri) throws URISyntaxException {
        return new URI(
            uri.getScheme(),
            null,
            uri.getHost(),
            uri.getPort(),
            uri.getPath(),
            uri.getQuery(),
            uri.getFragment()
        );
    }


}
