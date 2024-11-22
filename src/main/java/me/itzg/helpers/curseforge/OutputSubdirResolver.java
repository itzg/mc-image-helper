package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import me.itzg.helpers.curseforge.model.Category;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.ReactiveFileUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class OutputSubdirResolver {

    private final CategoryInfo categoryInfo;
    private final ConcurrentHashMap<String, Mono<Path>> subDirs
        = new ConcurrentHashMap<>();
    private final OutputPaths outputPaths;

    public OutputSubdirResolver(Path outputDir, CategoryInfo categoryInfo) {
        this.categoryInfo = categoryInfo;

        this.outputPaths = new OutputPaths(
            cachedDir(outputDir, "mods"),
            cachedDir(outputDir, "plugins"),
            cachedDir(outputDir, "saves")
        );
    }

    private static @NotNull Mono<Path> cachedDir(Path outputDir, String subdir) {
        return ReactiveFileUtils.createDirectories(outputDir.resolve(subdir)).cache();
    }

    @Data
    public static class Result {
        final Path dir;
        final boolean world;
    }

    public Mono<Result> resolve(CurseForgeMod modInfo) {
        final Category category = categoryInfo.contentClassIds.get(modInfo.getClassId());
        // applicable category?
        if (category == null) {
            return Mono.empty();
        }

        final Mono<Path> subDirMono;
        if (category.getSlug().endsWith("-mods")) {
            subDirMono = outputPaths.getModsDir();
        }
        else if (category.getSlug().endsWith("-plugins")) {
            subDirMono = outputPaths.getPluginsDir();
        }
        else if (category.getSlug().equals("worlds")) {
            subDirMono = outputPaths.getWorldsDir();
        }
        else {
            return Mono.error(
                new GenericException(
                    String.format("Unsupported category type=%s from mod=%s", category.getSlug(), modInfo.getSlug()))
            );
        }

        return subDirMono
            .map(path -> new Result(path, false));

    }
}
