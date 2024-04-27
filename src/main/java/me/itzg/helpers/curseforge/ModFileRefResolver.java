package me.itzg.helpers.curseforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.CurseForgeMod;
import me.itzg.helpers.curseforge.model.ModLoaderType;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ModFileRefResolver {

    private static final Pattern REF_FORMATS = Pattern.compile(
        "https://(www|legacy).curseforge.com/minecraft/(?<category>[A-Za-z-]+)/(?<slugInUrl>[A-Za-z][A-Za-z0-9-]*)(/files/(?<fileIdInUrl>\\d+))?"
            + "|"
            + "((?<modId>\\d+)|(?<slug>[A-Za-z][A-Za-z0-9-]*))((:(?<fileId>\\d+))|(@(?<fileMatcher>.+)))?"
    );
    private final CurseForgeApiClient apiClient;
    private final CategoryInfo categoryInfo;

    public ModFileRefResolver(CurseForgeApiClient apiClient, CategoryInfo categoryInfo) {
        this.apiClient = apiClient;
        this.categoryInfo = categoryInfo;
    }

    public Mono<List<CurseForgeFile>> resolveModFiles(List<String> modFileRefs, String defaultCategory,
        String gameVersion,
        ModLoaderType modLoaderType
    ) {
        return expandFileListings(modFileRefs)
            .flatMap(ref -> {
                final Matcher m = REF_FORMATS.matcher(ref);
                if (!m.matches()) {
                    return Mono.error(new InvalidParameterException("Mod file reference is not valid: " + ref));
                }

                final Integer modId = Optional.ofNullable(m.group("modId")).map(Integer::parseInt).orElse(null);
                final String fileIdWithModId = m.group("fileId");
                if (modId != null && fileIdWithModId != null) {
                    return apiClient.getModFileInfo(modId, Integer.parseInt(fileIdWithModId));
                }

                final String category = Optional.ofNullable(m.group("category"))
                    .orElse(defaultCategory);

                return resolveMod(modId, m, category)
                    .flatMap(curseForgeMod ->
                        resolveModFileFromMod(ref, gameVersion, category, modLoaderType, curseForgeMod, m)
                    );
            })
            .collectList();

    }

    @NotNull
    private static Flux<String> expandFileListings(List<String> modFileRefs) {
        return Flux.fromStream(modFileRefs.stream()
                .distinct()
                // handle @-file containing refs
                .flatMap(ref -> {
                    if (ref.startsWith("@")) {
                        try {
                            return Files.readAllLines(Paths.get(ref.substring(1))).stream()
                                .map(s -> s
                                    .replaceFirst("#.*", "")
                                    .trim()
                                )
                                .filter(s -> !s.isEmpty());
                        } catch (IOException e) {
                            throw new GenericException("Reading mod file refs from file", e);
                        }
                    }
                    else {
                        return Stream.of(ref);
                    }
                })
            )
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<? extends CurseForgeFile> resolveModFileFromMod(String ref, String gameVersion, String category,
        ModLoaderType modLoaderType, CurseForgeMod mod, Matcher m
    ) {
        final String fileId = Optional.ofNullable(m.group("fileIdInUrl"))
            .orElseGet(() -> m.group("fileId"));

        if (fileId != null) {
            return apiClient.getModFileInfo(mod.getId(), Integer.parseInt(fileId));
        }

        if (gameVersion == null) {
            return Mono.error(
                new InvalidParameterException("Game version is required to resolve mod reference: " + ref));
        }
        if (modLoaderType == null && Objects.equals(category, CurseForgeApiClient.CATEGORY_MC_MODS)) {
            return Mono.error(
                new InvalidParameterException("Mod loader is required to resolve mod reference: " + ref));
        }

        final String fileMatcherStr = m.group("fileMatcher");
        return mod.getLatestFilesIndexes().stream()
            .filter(fileIndex ->
                fileIndex.getGameVersion().equals(gameVersion)
                    && (
                    // only mods require a specific mod loader, plugins are not that specific
                    !Objects.equals(category, CurseForgeApiClient.CATEGORY_MC_MODS)
                        // mod publisher didn't specifify a modloader...will just have to assume it's the type we need
                        || fileIndex.getModLoader() == null
                        // but normally make sure it matches
                        || fileIndex.getModLoader().equals(modLoaderType))
                    && (fileMatcherStr == null || fileIndex.getFilename().contains(fileMatcherStr))
            )
            .findFirst()
            .map(fileIndex -> mod.getLatestFiles().stream()
                .filter(curseForgeFile -> curseForgeFile.getId() == fileIndex.getFileId())
                .findFirst()
                .map(Mono::just)
                .orElseGet(() -> apiClient.getModFileInfo(mod.getId(), fileIndex.getFileId()))
            )
            .orElseGet(() -> Mono.error(
                new InvalidParameterException("Unable to find match in latest files for " + ref))
            );
    }

    public static ModFileIds idsFrom(CurseForgeFile curseForgeFile) {
        return new ModFileIds(curseForgeFile.getModId(), curseForgeFile.getId());
    }

    private Mono<CurseForgeMod> resolveMod(Integer modId, Matcher m, String categorySlug) {
        final Mono<CurseForgeMod> resolvedMod;
        if (modId != null) {
            resolvedMod = apiClient.getModInfo(modId);
        }
        else {
            final String slug = Optional.ofNullable(m.group("slugInUrl"))
                .orElseGet(() -> m.group("slug"));

            if (categorySlug == null) {
                throw new InvalidParameterException("Default category is required to resolve " + slug);
            }

            final int categoryClassId = categoryInfo.getClassIdForSlug(categorySlug);

            resolvedMod = apiClient.searchMod(slug, categoryClassId);
        }
        return resolvedMod;
    }
}
