package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.model.Category;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.files.ReactiveFileUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

@Slf4j
public class OutputSubdirResolver {

    private final CategoryInfo categoryInfo;
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
            log.debug("Skipping output of project file '{}' (id={}) with class ID {}",
                modInfo.getName(), modInfo.getId(), modInfo.getClassId()
                );
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
            return Mono.empty();
        }

        return subDirMono
            .map(path -> new Result(path, false));

    }
}
