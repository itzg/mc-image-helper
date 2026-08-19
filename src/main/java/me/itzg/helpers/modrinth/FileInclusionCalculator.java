package me.itzg.helpers.modrinth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.files.MultiMatcher;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex;

@Slf4j
public class FileInclusionCalculator {

    private final List<MultiMatcher> excludeFiles;
    private final List<MultiMatcher> forceIncludeFiles;

    public FileInclusionCalculator(
        String modpackProjectSlug,
        List<String> excludeFiles,
        List<String> forceIncludeFiles,
        ExcludeIncludesContent excludeIncludesContent) {

        final Set<String> excludePatterns = new HashSet<>();
        final Set<String> forceIncludePatterns = new HashSet<>();

        if (excludeFiles != null) {
            excludePatterns.addAll(excludeFiles);
        }
        if (forceIncludeFiles != null) {
            forceIncludePatterns.addAll(forceIncludeFiles);
        }
        if (excludeIncludesContent != null) {
            addAll(excludeIncludesContent.getGlobalExcludes(), excludePatterns);
            addAll(excludeIncludesContent.getGlobalForceIncludes(), forceIncludePatterns);

            if (excludeIncludesContent.getModpacks() != null && modpackProjectSlug != null) {
                final ExcludeIncludes modpack = excludeIncludesContent.getModpacks().get(modpackProjectSlug);
                if (modpack != null) {
                    addAll(modpack.getExcludes(), excludePatterns);
                    addAll(modpack.getForceIncludes(), forceIncludePatterns);
                }
            }
        }

        this.excludeFiles = createMatchers(excludePatterns);
        this.forceIncludeFiles = createMatchers(forceIncludePatterns);
    }

    public static FileInclusionCalculator empty() {
        return new FileInclusionCalculator(null, null, null, null);
    }

    boolean includeModFile(ModpackIndex.ModpackFile modFile) {
        return shouldForceIncludeFile(modFile.getPath())
            || (
            // env is optional
            (modFile.getEnv() == null
                || modFile.getEnv().get(Env.server) != EnvType.unsupported)
                && !shouldExcludeFile(modFile.getPath())
        );
    }

    private boolean shouldForceIncludeFile(String modPath) {
        if (forceIncludeFiles.isEmpty()) {
            return false;
        }

        final String normalized = FileInclusionCalculator.sanitizeModFilePath(modPath).toLowerCase();

        final boolean include = forceIncludeFiles.stream()
            .anyMatch(matcher -> matcher.matches(normalized));
        if (include) {
            log.debug("Force including '{}' as requested", modPath);
        }

        return include;
    }

    private boolean shouldExcludeFile(String modPath) {
        if (excludeFiles.isEmpty()) {
            return false;
        }

        // to match case-insensitive
        final String normalized = FileInclusionCalculator.sanitizeModFilePath(modPath).toLowerCase();

        final boolean exclude = excludeFiles.stream()
            .anyMatch(matcher -> matcher.matches(normalized));
        if (exclude) {
            log.debug("Excluding '{}' as requested", modPath);
        }
        return exclude;
    }

    private List<MultiMatcher> createMatchers(Set<String> patterns) {
        return patterns.stream()
            .map(pattern -> new MultiMatcher(pattern.toLowerCase()))
            .collect(Collectors.toList());
    }

    static String sanitizeModFilePath(String path) {
        // Using only backslash delimiters and not forward slashes?
        // (mixed usage will assume backslashes were purposeful)
        if (path.contains("\\") && !path.contains("/")) {
            return path.replace("\\", "/");
        }
        else {
            return path;
        }
    }

    private void addAll(Set<String> from, Set<String> into) {
        if (from != null) {
            into.addAll(from);
        }
    }
}
