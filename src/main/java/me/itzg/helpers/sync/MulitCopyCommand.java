package me.itzg.helpers.sync;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;
import static me.itzg.helpers.singles.NormalizeOptions.normalizeOptionList;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ReactiveFileUtils;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.Uris;
import org.jetbrains.annotations.Blocking;
import org.reactivestreams.Publisher;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Command(name = "mcopy", description = "Multi-source file copy operation with with managed cleanup. "
    + "Supports auto-detected sourcing from file list, directories, and URLs")
@Slf4j
public class MulitCopyCommand implements Callable<Integer> {
    @SuppressWarnings("unused")
    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean showHelp;

    @Option(names = {"--manifest-id", "--scope"},
        description = "If managed cleanup is required, this is the identifier used for qualifying manifest filename in destination"
    )
    String manifestId;

    @Option(names = {"--to", "--output-directory"}, required = true)
    Path dest;

    @Option(names = "--glob", defaultValue = "*", paramLabel = "GLOB",
        description = "When a source is a directory, this filename glob will be applied to select files."
    )
    String fileGlob;

    @Option(names = "--file-is-listing",
        description = "Source files or URLs are processed as a line delimited list of sources.\n" +
            "For remote listing files, the contents must all be file URLs."
    )
    boolean fileIsListingOption;

    @Option(names = {"--skip-up-to-date", "-z"}, defaultValue = "true")
    boolean skipUpToDate;

    @Option(names = "--skip-existing", defaultValue = "false")
    boolean skipExisting;

    @Option(names = "--quiet-when-skipped", description = "Don't log when file exists or is up to date")
    boolean quietWhenSkipped;

    @Option(names = "--ignore-missing-sources", description = "Don't log or fail exit code when any or all sources are missing")
    boolean ignoreMissingSources;

    @Parameters(split = SPLIT_COMMA_NL, splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "SRC",
        description = "Any mix of source file, directory, or URLs delimited by commas or newlines"
            + "%nPer-file destinations can be assigned by destination<source"
            + "%nEmbedded comments are allowed."
    )
    public void setSources(List<String> sources) {
        this.sources = normalizeOptionList(sources);
    }
    List<String> sources = Collections.emptyList();

    private final static String destinationDelimiter = "<";

    @Override
    public Integer call() throws Exception {
        final List<Path> results = Flux.fromIterable(sources)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .flatMap(source -> processSource(source, fileIsListingOption, dest))
            .collectList()
            .block();

        cleanupAndSaveManifest(results);

        return ExitCode.OK;
    }

    private void cleanupAndSaveManifest(List<Path> paths) {
        if (manifestId != null) {
            final MultiCopyManifest prevManifest = Manifests.load(dest, manifestId, MultiCopyManifest.class);

            final MultiCopyManifest newManifest = MultiCopyManifest.builder()
                .files(Manifests.relativizeAll(dest, paths))
                .build();

            try {
                Manifests.cleanup(dest, prevManifest, newManifest, log);
            } catch (IOException e) {
                log.warn("Failed to cleanup from previous manifest");
            }

            Manifests.apply(dest, manifestId, newManifest);
        }
    }

    private Publisher<Path> processSource(String source, boolean fileIsListing, Path parentDestination) {
        final Path destination;
        final String resolvedSource;

        final int delimiterPos = source.indexOf(destinationDelimiter);
        if (delimiterPos > 0) {
            destination = parentDestination.resolve(Paths.get(source.substring(0, delimiterPos)));
            resolvedSource = source.substring(delimiterPos + 1);
        }
        else {
            destination = parentDestination;
            resolvedSource = source;
        }

        if (fileIsListing) {
            if (Uris.isUri(resolvedSource)) {
                return processRemoteListingFile(resolvedSource, destination);
            } else {
                final Path path = Paths.get(resolvedSource);
                if (Files.isDirectory(path)) {
                    throw new GenericException(String.format("Specified listing file '%s' is a directory", resolvedSource));
                }
                if (!Files.exists(path)) {
                    throw new GenericException(String.format("Source file '%s' does not exist", resolvedSource));
                }
                return processListingFile(path, destination);
            }
        }

        return ReactiveFileUtils.createDirectories(destination)
            .flatMapMany(ignored -> {
                if (Uris.isUri(resolvedSource)) {
                    return processRemoteSource(resolvedSource, destination);
                } else {
                    final Path path = Paths.get(resolvedSource);
                    if (!Files.exists(path)) {
                        return Mono.error(new InvalidParameterException(String.format("Source file '%s' does not exist", resolvedSource)));
                    }

                    if (Files.isDirectory(path)) {
                        return processDirectory(path, destination);
                    } else {
                        return processFile(path, destination);
                    }
                }
            });
    }

    private Flux<Path> processListingFile(Path listingFile, Path destination) {
        return Mono.just(listingFile)
            .publishOn(Schedulers.boundedElastic())
            .flatMapMany(path -> {
                try {
                    @SuppressWarnings("BlockingMethodInNonBlockingContext") // false warning from IntelliJ
                    final List<String> lines = Files.readAllLines(path);
                    return Flux.fromIterable(lines)
                        .filter(this::isListingLine)
                        .flatMap(src -> processSource(src,
                            // avoid recursive file-listing processing
                            false,
                            destination));
                } catch (IOException e) {
                    return Mono.error(new GenericException("Failed to read file listing from " + path));
                }
            });
    }

    private Mono<Path> processFile(Path source, Path destination) {

        return Mono.just(source)
            .publishOn(Schedulers.boundedElastic())
            .map(path -> processFileImmediate(source, destination));
    }

    /**
     * Non-mono version of {@link #processFile(Path, Path)}
     *
     * @param scopedDest allows for sub-directory destinations
     */
    @Blocking
    private Path processFileImmediate(Path source, Path scopedDest) {
        if (!Files.exists(source)) {
            throw new InvalidParameterException("Source file does not exist: " + source);
        }

        final Path destFile = scopedDest.resolve(source.getFileName());

        if (Files.exists(destFile)) {
            try {
                if (Files.size(source) != Files.size(destFile)) {
                    if (Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(destFile)) > 0) {
                        log.info("Copying over existing file {} since {} is newer",
                            destFile, source
                        );

                        Files.copy(source, destFile, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        forSkipped().log("Skipping existing={} since it is newer than source={}", destFile, source);
                    }
                } else {
                    forSkipped().log("Skipping existing={} since it has same size as source={}", destFile, source);
                }
            } catch (IOException e) {
                throw new GenericException("Failed to evaluate/copy existing file", e);
            }
        } else {
            try {
                log.info("Copying new file from {} to {}", source, destFile);

                Files.copy(source, destFile);
            } catch (IOException e) {
                throw new GenericException("Failed to copy new file", e);
            }
        }

        return destFile;
    }

    private Flux<Path> processDirectory(Path srcDir, Path destination) {
        return Mono.just(srcDir)
            .publishOn(Schedulers.boundedElastic())
            .flatMapMany(path -> {
                if (!Files.exists(srcDir)) {
                    return Mono.error(new InvalidParameterException("Source directory does not exist: " + srcDir));
                }
                if (!Files.isDirectory(srcDir)) {
                    return Mono.error(new InvalidParameterException("Source is not a directory: " + srcDir));
                }

                try {
                    final ArrayList<Path> results = new ArrayList<>();
                    //noinspection BlockingMethodInNonBlockingContext because IntelliJ is confused
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(srcDir, fileGlob)) {
                        for (final Path file : files) {
                            //noinspection BlockingMethodInNonBlockingContext because IntelliJ is confused
                            results.add(processFileImmediate(file, destination));
                        }
                    }
                    return Flux.fromIterable(results);
                } catch (IOException e) {
                    throw new GenericException("Failed to process directory source", e);
                }
            });
    }

    private Mono<Path> processRemoteSource(String source, Path destination) {
        return Fetch.fetch(URI.create(source))
            .userAgentCommand("mcopy")
            .toDirectory(destination)
            .skipUpToDate(skipUpToDate)
            .skipExisting(skipExisting)
            .handleDownloaded((downloaded, uri, size) ->
                log.debug("Downloaded {} from {} ({} bytes)", downloaded, uri, size)
            )
            .handleStatus((status, uri, file) -> {
                switch (status) {
                    case DOWNLOADING:
                        break;
                    case SKIP_FILE_UP_TO_DATE:
                        forSkipped().log("The file {} is already up to date", file);
                        break;
                    case SKIP_FILE_EXISTS:
                        forSkipped().log("The file {} already exists", file);
                        break;
                    case DOWNLOADED:
                        log.info("Downloaded {} from {}", file, uri);
                        break;
                }
            })
            .assemble()
            .onErrorResume(throwable -> ignoreMissingSources && FailedRequestException.isNotFound(throwable),
                throwable -> Mono.empty()
            )
            .checkpoint("Retrieving " + source, true);
    }

    private LoggingEventBuilder forSkipped() {
        return log.atLevel(quietWhenSkipped ? Level.DEBUG : Level.INFO);
    }

    private Flux<Path> processRemoteListingFile(String source, Path destination) {
        @SuppressWarnings("resource") // closed on terminate
        SharedFetch sharedFetch = Fetch.sharedFetch("mcopy", SharedFetch.Options.builder().build());
        return Mono.just(source)
            .flatMapMany(s ->
                sharedFetch.fetch(URI.create(source))
                    .asString().assemble()
                    .flatMapMany(content -> Flux.just(content.split("\\r?\\n")))
                    .filter(this::isListingLine)
            )
            .flatMap(url -> processSource(url, false, destination))
            .doOnTerminate(sharedFetch::close)
            .checkpoint("Processing remote listing at " + source, true);
    }

    private boolean isListingLine(String s) {
        return !s.startsWith("#")
            && !s.isEmpty();
    }

}
