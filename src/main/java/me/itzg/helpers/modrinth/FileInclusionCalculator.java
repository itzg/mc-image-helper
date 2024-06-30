package me.itzg.helpers.modrinth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.ExcludeIncludesContent.ExcludeIncludes;
import me.itzg.helpers.modrinth.model.Env;
import me.itzg.helpers.modrinth.model.EnvType;
import me.itzg.helpers.modrinth.model.ModpackIndex;

@Slf4j
public class FileInclusionCalculator {

    private final Set<String> excludeFiles;
    private final Set<String> forceIncludeFiles;

    public FileInclusionCalculator(
        String modpackProjectSlug,
        List<String> excludeFiles,
        List<String> forceIncludeFiles,
        ExcludeIncludesContent excludeIncludesContent) {

        this.excludeFiles = new HashSet<>();
        this.forceIncludeFiles = new HashSet<>();

        if (excludeFiles != null) {
            this.excludeFiles.addAll(excludeFiles);
        }
        if (forceIncludeFiles != null) {
            this.forceIncludeFiles.addAll(forceIncludeFiles);
        }
        if (excludeIncludesContent != null) {
            addAll(excludeIncludesContent.getGlobalExcludes(), this.excludeFiles);
            addAll(excludeIncludesContent.getGlobalForceIncludes(), this.forceIncludeFiles);

            if (excludeIncludesContent.getModpacks() != null && modpackProjectSlug != null) {
                final ExcludeIncludes modpack = excludeIncludesContent.getModpacks().get(modpackProjectSlug);
                if (modpack != null) {
                    addAll(modpack.getExcludes(), this.excludeFiles);
                    addAll(modpack.getForceIncludes(), this.forceIncludeFiles);
                }
            }
        }
    }

    public static FileInclusionCalculator empty() {
        return new FileInclusionCalculator(null, null, null, null);
    }

    boolean includeModFile(ModpackIndex.ModpackFile modFile) {
        return (
            // env is optional
            modFile.getEnv() == null
                || modFile.getEnv().get(Env.server) != EnvType.unsupported
                || shouldForceIncludeFile(modFile.getPath())
        )
            && !shouldExcludeFile(modFile.getPath());
    }

    private boolean shouldForceIncludeFile(String modPath) {
        if (forceIncludeFiles == null || forceIncludeFiles.isEmpty()) {
            return false;
        }

        final String normalized = FileInclusionCalculator.sanitizeModFilePath(modPath).toLowerCase();

        final boolean include = forceIncludeFiles.stream()
            .anyMatch(s -> normalized.contains(s.toLowerCase()));
        if (include) {
            log.debug("Force including '{}' as requested", modPath);
        }

        return include;
    }

    private boolean shouldExcludeFile(String modPath) {
        if (excludeFiles == null || excludeFiles.isEmpty()) {
            return false;
        }

        // to match case-insensitive
        final String normalized = FileInclusionCalculator.sanitizeModFilePath(modPath).toLowerCase();

        final boolean exclude = excludeFiles.stream()
            .anyMatch(s -> normalized.contains(s.toLowerCase()));
        if (exclude) {
            log.debug("Excluding '{}' as requested", modPath);
        }
        return exclude;
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
