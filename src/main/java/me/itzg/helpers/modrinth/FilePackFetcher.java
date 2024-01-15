package me.itzg.helpers.modrinth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import reactor.core.publisher.Mono;

@Slf4j
public class FilePackFetcher implements ModrinthPackFetcher {

    private final ProjectRef projectRef;

    public FilePackFetcher(ProjectRef projectRef) {
        if (!projectRef.isFileUri()) {
            throw new IllegalArgumentException("Requires a projectRef with a file URI");
        }
        this.projectRef = projectRef;
    }

    @Override
    public Mono<FetchedPack> fetchModpack(ModrinthModpackManifest prevManifest) {
        final Path file = Paths.get(projectRef.getProjectUri());
        if (!Files.exists(file)) {
            throw new InvalidParameterException("Local modpack file does not exist: " + file);
        }

        return Mono.just(
            new FetchedPack(file, projectRef.getIdOrSlug(), deriveVersionId(), "local")
        );
    }

    private String deriveVersionId() {
        final Path file = Paths.get(projectRef.getProjectUri());

        try {
            final FileTime fileTime = Files.getLastModifiedTime(file);
            return fileTime.toString();
        } catch (IOException e) {
            log.warn("Unable to retrieve modified file time", e);
            return "unknown";
        }
    }
}
