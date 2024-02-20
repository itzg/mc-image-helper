package me.itzg.helpers.curseforge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.files.AntPathMatcher;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

@Slf4j
public class OverridesFromZipApplier implements OverridesApplier {

    public static final String LEVEL_DAT_SUFFIX = "/level.dat";
    public static final int LEVEL_DAT_SUFFIX_LEN = LEVEL_DAT_SUFFIX.length();

    private final Path outputDir;
    private final Path modpackZip;
    private final boolean overridesSkipExisting;
    private final String overridesDir;
    private final LevelFrom levelFrom;
    private final AntPathMatcher overridesExclusionsMatcher;

    public OverridesFromZipApplier(
        Path outputDir,
        Path modpackZip,
        boolean overridesSkipExisting,
        String overridesDir,
        LevelFrom levelFrom,
        List<String> overridesExclusions
    ) {
        this.outputDir = outputDir;
        this.modpackZip = modpackZip;
        this.overridesSkipExisting = overridesSkipExisting;
        this.overridesDir = overridesDir;
        this.levelFrom = levelFrom;
        this.overridesExclusionsMatcher = new AntPathMatcher(overridesExclusions);
    }

    @Override
    public Result apply() throws IOException {
        log.debug("Applying overrides from '{}' in zip file", overridesDir);

        final String levelEntryName = findLevelEntryInOverrides(modpackZip, overridesDir);
        final String levelEntryNamePrefix = levelEntryName != null ? levelEntryName + "/" : null;

        final boolean worldOutputDirExists = levelEntryName != null &&
            Files.exists(outputDir.resolve(levelEntryName));

        log.debug("While applying overrides, found level entry='{}' in modpack overrides and worldOutputDirExists={}",
            levelEntryName, worldOutputDirExists
        );

        final String overridesDirPrefix = overridesDir + "/";
        final int overridesPrefixLen = overridesDirPrefix.length();

        final List<Path> overrides = new ArrayList<>();
        try (ZipFile zip = ZipFile.builder().setPath(modpackZip).get()) {

            final Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                final ZipArchiveEntry entry = entries.nextElement();

                if (entry.getName().startsWith(overridesDirPrefix)) {
                    if (!entry.isDirectory()) {
                        if (log.isTraceEnabled()) {
                            log.trace("Processing override entry={}:{}", entry.isDirectory() ? "D" : "F", entry.getName());
                        }
                        final String subpath = entry.getName().substring(overridesPrefixLen);

                        if (overridesExclusionsMatcher.matches(subpath)) {
                            continue;
                        }

                        final Path outPath = outputDir.resolve(subpath);

                        // Rules
                        // - don't ever overwrite world data
                        // - user has option to not overwrite any existing file from overrides
                        // - otherwise user will want latest modpack's overrides content

                        final boolean isInWorldDirectory = levelEntryNamePrefix != null &&
                            subpath.startsWith(levelEntryNamePrefix);

                        if (worldOutputDirExists && isInWorldDirectory) {
                            continue;
                        }

                        if (!(overridesSkipExisting && Files.exists(outPath))) {
                            log.trace("Applying override {}", subpath);
                            // zip files don't always list the directories before the files, so just create-as-needed
                            Files.createDirectories(outPath.getParent());
                            try (InputStream entryStream = zip.getInputStream(entry)) {
                                Files.copy(entryStream, outPath, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                        else {
                            log.trace("Skipping override={} since the file already existed", subpath);
                        }

                        // Track this path for later cleanup
                        // UNLESS it is within a world/level directory
                        if (levelEntryName == null || !isInWorldDirectory) {
                            overrides.add(outPath);
                        }
                    }
                }
            }
        }

        return new Result(overrides,
            levelFrom == LevelFrom.OVERRIDES ? levelEntryName : null
        );
    }

    /**
     * @return if present, the subpath to a world/level directory with the overrides prefix removed otherwise null
     */
    private String findLevelEntryInOverrides(Path modpackZip, String overridesDir) throws IOException {
        try (ZipFile zipFile = ZipFile.builder().setPath(modpackZip).get()) {
            final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                final ZipArchiveEntry entry = entries.nextElement();
                final String name = entry.getName();
                if (!entry.isDirectory() && name.startsWith(overridesDir + "/") && name.endsWith(LEVEL_DAT_SUFFIX)) {
                    return name.substring(overridesDir.length() + 1, name.length() - LEVEL_DAT_SUFFIX_LEN);
                }
            }
        }

        return null;
    }

}
