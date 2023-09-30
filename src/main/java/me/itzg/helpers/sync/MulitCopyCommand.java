package me.itzg.helpers.sync;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.Uris;
import org.jetbrains.annotations.Blocking;
import org.reactivestreams.Publisher;
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

    @Parameters(split = SPLIT_COMMA_NL, splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL, arity = "1..*",
        paramLabel = "SRC",
        description = "Any mix of source file, directory, or URLs."
            + "%nCan be optionally comma or newline separated."
    )
    List<String> sources;

    @Override
    public Integer call() throws Exception {

        Files.createDirectories(dest);

        Flux.fromIterable(sources)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .flatMap(source -> processSource(source, fileIsListingOption))
            .collectList()
            .flatMap(this::cleanupAndSaveManifest)
            .block();

        return ExitCode.OK;
    }

    private Mono<?> cleanupAndSaveManifest(List<Path> paths) {
        return Mono.justOrEmpty(manifestId)
            .publishOn(Schedulers.boundedElastic())
            .map(s -> {
                final MultiCopyManifest prevManifest = Manifests.load(dest, manifestId, MultiCopyManifest.class);

                final MultiCopyManifest newManifest = MultiCopyManifest.builder()
                    .files(Manifests.relativizeAll(dest, paths))
                    .build();

                try {
                    Manifests.cleanup(dest, prevManifest, newManifest, log);
                } catch (IOException e) {
                    log.warn("Failed to cleanup from previous manifest");
                }

                return Manifests.save(dest, manifestId, newManifest);
            });
    }

    private Publisher<Path> processSource(String source, boolean fileIsListing) {
        if (Uris.isUri(source)) {
            return fileIsListing ? processRemoteListingFile(source) : processRemoteSource(source);
        } else {
            final Path path = Paths.get(source);
            if (!Files.exists(path)) {
                throw new GenericException(String.format("Source file '%s' does not exist", source));
            }

            if (Files.isDirectory(path)) {
                return processDirectory(path);
            } else {
                return fileIsListing ? processListingFile(path) : processFile(path);
            }
        }
    }

    private Flux<Path> processListingFile(Path listingFile) {
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
                            false));
                } catch (IOException e) {
                    return Mono.error(new GenericException("Failed to read file listing from " + path));
                }
            });
    }

    private Mono<Path> processFile(Path source) {

        return Mono.just(source)
            .publishOn(Schedulers.boundedElastic())
            .map(path -> processFileImmediate(source, dest));
    }

    /**
     * Non-mono version of {@link #processFile(Path)}
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
                        log.debug("Copying over existing={} file since source={} file is newer",
                            destFile, source
                        );

                        Files.copy(source, destFile, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.debug("Skipping existing={} since it is newer than source={}",
                            destFile, source
                        );
                    }
                } else {
                    log.debug("Skipping existing={} since it has same size as source={}",
                        destFile, source
                    );
                }
            } catch (IOException e) {
                throw new GenericException("Failed to evaluate/copy existing file", e);
            }
        } else {
            try {
                log.debug("Copying new file from={} to={}", source, destFile);

                Files.copy(source, destFile);
            } catch (IOException e) {
                throw new GenericException("Failed to copy new file", e);
            }
        }

        return destFile;
    }

    private Flux<Path> processDirectory(Path srcDir) {
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
                            results.add(processFileImmediate(file, dest));
                        }
                    }
                    return Flux.fromIterable(results);
                } catch (IOException e) {
                    throw new GenericException("Failed to process directory source", e);
                }
            });
    }

    private Mono<Path> processRemoteSource(String source) {
        return Fetch.fetch(URI.create(source))
            .userAgentCommand("mcopy")
            .toDirectory(dest)
            .skipUpToDate(skipUpToDate)
            .skipExisting(skipExisting)
            .handleStatus((status, uri, file) -> {
                switch (status) {
                    case DOWNLOADING:
                        log.info("Downloading {} from {}", file, uri);
                        break;
                    case SKIP_FILE_UP_TO_DATE:
                        log.info("The file {} is already up to date", file);
                        break;
                    case SKIP_FILE_EXISTS:
                        log.info("The file {} already exists", file);
                        break;
                    case DOWNLOADED:
                        log.debug("Finished downloading to file={}", file);
                        break;
                }
            })
            .assemble()
            .checkpoint("Retrieving " + source, true);
    }

    private Flux<Path> processRemoteListingFile(String source) {
        @SuppressWarnings("resource") // closed on terminate
        SharedFetch sharedFetch = Fetch.sharedFetch("mcopy", SharedFetch.Options.builder().build());
        return Mono.just(source)
            .flatMapMany(s ->
                sharedFetch.fetch(URI.create(source))
                    .asString().assemble()
                    .flatMapMany(content -> Flux.just(content.split("\\r?\\n")))
                    .filter(this::isListingLine)
            )
            .flatMap(this::processRemoteSource)
            .doOnTerminate(sharedFetch::close)
            .checkpoint("Processing remote listing at " + source, true);
    }

    private boolean isListingLine(String s) {
        return !s.startsWith("#")
            && !s.isEmpty();
    }

}
