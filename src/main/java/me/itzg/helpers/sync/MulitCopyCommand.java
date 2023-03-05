package me.itzg.helpers.sync;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.Uris;
import org.reactivestreams.Publisher;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "mcopy", description = "Multi-source file copy operation with with managed cleanup. "
    + "Supports auto-detected sourcing from file list, directories, and URLs")
@Slf4j
public class MulitCopyCommand implements Callable<Integer> {

    @Option(names = {"--manifest-id", "--scope"},
        description = "If managed cleanup is required, this is the identifier used for qualifying manifest filename in destination"
    )
    String manifestId;

    @Option(names = {"--to"}, required = true)
    Path dest;

    @Option(names = "--glob", defaultValue = "*",
        description = "When a source is a directory, this filename glob will be applied to select files."
    )
    String fileGlob;

    @Parameters(split = ",", arity = "1..*")
    List<String> sources;

    @Override
    public Integer call() throws Exception {

        Files.createDirectories(dest);

        Flux.fromIterable(sources)
            .flatMap(this::processSource)
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

    private Publisher<Path> processSource(String source) {
        if (Uris.isUri(source)) {
            return processRemoteSource(source);
        }
        else {
            final Path path = Paths.get(source);
            if (!Files.exists(path)) {
                throw new GenericException(String.format("Source file '%s' does not exist", source));
            }

            if (Files.isDirectory(path)) {
                return processDirectory(path);
            }
            else {
                return processFile(path);
            }
        }
    }

    private Mono<Path> processFile(Path source) {

        return Mono.just(source)
            .publishOn(Schedulers.boundedElastic())
            .map(path -> processFileImmediate(source, dest));
    }

    /**
     * Non-mono version of {@link #processFile(Path)}
     * @param scopedDest allows for sub-directory destinations
     */
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
                    }
                    else {
                        log.debug("Skipping existing={} since it is newer than source={}",
                            destFile, source
                        );
                    }
                }
                else {
                    log.debug("Skipping existing={} since it has same size as source={}",
                        destFile, source
                    );
                }
            } catch (IOException e) {
                throw new GenericException("Failed to evaluate/copy existing file", e);
            }
        }
        else {
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
                    //noinspection BlockingMethodInNonBlockingContext since IntelliJ flags incorrectly
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(srcDir, fileGlob)) {
                        for (final Path file : files) {
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
            .skipExisting(true)
            .handleStatus((status, uri, file) -> {
                switch (status) {
                    case DOWNLOADING:
                        log.info("Downloading uri={} to file={}", uri, file);
                        break;
                    case DOWNLOADED:
                        log.debug("Finished downloading to file={}", file);
                        break;
                }
            })
            .assemble();
    }

}
