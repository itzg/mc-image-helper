package me.itzg.helpers.curseforge;

import static me.itzg.helpers.http.Fetch.fetch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.itzg.helpers.McImageHelper;
import me.itzg.helpers.cache.CacheArgs;
import me.itzg.helpers.curseforge.ModpacksPageUrlParser.Parsed;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.files.TabularOutput;
import me.itzg.helpers.http.PathOrUri;
import me.itzg.helpers.http.PathOrUriConverter;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.json.ObjectMappers;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "install-curseforge",
    description = "Downloads, installs, and upgrades CurseForge modpacks",
    subcommands = {SchemasCommand.class}
)
public class InstallCurseForgeCommand implements Callable<Integer> {

    @Option(names = {"--help","-h"}, usageHelp = true)
    boolean help;

    @Option(names = {"--output-directory", "-o"}, defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--modpack-page-url", paramLabel = "URL",
        description = "URL of a modpack page such as "
            + "%nhttps://www.curseforge.com/minecraft/modpacks/all-the-mods-8"
            + "or a file's page " +
            "%nhttps://www.curseforge.com/minecraft/modpacks/all-the-mods-8/files/4248390")
    String pageUrl;

    @Option(names = "--slug", description = "The short-URL identifier")
    String slug;

    @Option(names = "--file-id")
    Integer fileId;

    @Option(names = "--modpack-zip", paramLabel = "PATH",
        description = "Path to a pre-downloaded modpack client zip file that can be used when modpack author disallows automation." +
            "%nCan also be passed via " + CurseForgeInstaller.MODPACK_ZIP_VAR,
        defaultValue = "${env:" + CurseForgeInstaller.MODPACK_ZIP_VAR + "}"
    )
    Path modpackZip;

    @Option(names = "--modpack-manifest", paramLabel = "PATH",
        description = "Similar to --modpack-zip but provide the manifest.json from the modpack."
            + "%nCan be a local file path or a URL to a manifest."
    )
    String modpackManifest;

    @Option(names = "--downloads-repo", paramLabel = "DIR",
        description = "A local directory that will supply pre-downloaded mod and modpack files that " +
            "are marked disallowed for automated download." +
            " The subdirectories mods, modpacks, and worlds will also be consulted accordingly."
    )
    Path downloadsRepo;

    @Option(names = "--api-base-url", defaultValue = "${env:CF_API_BASE_URL}",
        description = "Allows for overriding the CurseForge Eternal API used")
    String apiBaseUrl;

    @Option(names = "--api-key", defaultValue = "${env:" + CurseForgeInstaller.API_KEY_VAR + "}",
        description = "An API key allocated from the Eternal developer console at "
            + CurseForgeInstaller.ETERNAL_DEVELOPER_CONSOLE_URL +
            "%nCan also be passed via " + CurseForgeInstaller.API_KEY_VAR
    )
    String apiKey;

    @ArgGroup(exclusive = false)
    ExcludeIncludeArgs excludeIncludeArgs = new ExcludeIncludeArgs();

    static class ExcludeIncludeArgs {
        @ArgGroup(exclusive = false)
        Listed listed;

        @Option(names = "--exclude-include-file", paramLabel = "FILE|URI",
            description = "A JSON file that contains global and per modpack exclude/include declarations. "
                + "See README for schema.",
            converter = PathOrUriConverter.class
        )
        PathOrUri excludeIncludeFile;

        static class Listed {
            @Option(names = "--exclude-mods", paramLabel = "PROJECT_ID|SLUG",
                split = "\\s+|,", splitSynopsisLabel = ",|<ws>",
                description = "For mods that need to be excluded from server deployments, such as those that don't label as client"
            )
            Set<String> excludedMods;

            @Option(names = "--force-include-mods", paramLabel = "PROJECT_ID|SLUG",
                split = "\\s+|,", splitSynopsisLabel = ",|<ws>",
                description = "Some mods incorrectly declare client-only support, but still need to be included in a server deploy."
                    + "%nThis can also be used to selectively override exclusions."
            )
            Set<String> forceIncludeMods;

        }
    }

    @Option(names = "--filename-matcher", paramLabel = "STR",
        description = "Substring to select specific modpack filename")
    String filenameMatcher;

    @Option(names = "--force-synchronize")
    boolean forceSynchronize;

    @Option(names = "--force-reinstall-modloader")
    boolean forceReinstallModloader;

    @Option(names = "--ignore-missing-files",
        split = McImageHelper.SPLIT_COMMA_NL, splitSynopsisLabel = McImageHelper.SPLIT_SYNOPSIS_COMMA_NL,
        description = "These files will be ignored when evaluating if the modpack is up to date"
    )
    List<String> ignoreMissingFiles;

    @Option(names = "--set-level-from",
        description = "When WORLD_FILE, a world file included the modpack will be unzipped into a folder under 'saves' and referenced as 'LEVEL' in the results file."
            + "\nWhen OVERRIDES and the overrides contains a world save directory (contains level.dat), then that directory will be referenced as 'LEVEL' in the results file."
            + "\nIn either case, existing world data will be preserved and skipped if it already exists."
            + "\nValid values: ${COMPLETION-CANDIDATES}"
    )
    LevelFrom levelFrom;

    @Option(names = "--overrides-skip-existing",
        description = "When enabled, existing files will not be replaced by overrides content from the modpack"
    )
    boolean overridesSkipExisting;

    @Option(names = "--overrides-exclusions",
        split = "\n|,", splitSynopsisLabel = "NL or ,",
        description = "Excludes files from the overrides that match these ant-style patterns\n"
            + "*  : matches any non-slash characters\n"
            + "** : matches any characters\n"
            + "?  : matches one character"
    )
    List<String> overridesExclusions;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Option(names = "--missing-mods-filename", defaultValue = "MODS_NEED_DOWNLOAD.txt")
    String missingModsFilename;

    @Option(names = "--disable-api-caching", defaultValue = "${env:CF_DISABLE_API_CACHING:-false}")
    boolean disableApiCaching;

    @ArgGroup(exclusive = false)
    CacheArgs cacheArgs;

    @Override
    public Integer call() throws Exception {
        // https://www.curseforge.com/minecraft/modpacks/all-the-mods-8/files
        // https://www.curseforge.com/minecraft/modpacks/all-the-mods-8/files/4248390

        if (pageUrl != null) {
            final Parsed parsed = ModpacksPageUrlParser.parse(pageUrl);
            slug = parsed.getSlug();
            fileId = parsed.getFileId();
        }

        if (slug == null) {
            if (modpackZip != null) {
                System.err.println("A modpack page URL or slug identifier is required even with a provided modpack zip");
            } else if (modpackManifest != null) {
                System.err.println("A modpack page URL or slug identifier is required even with a provided modpack manifest");
            } else {
                System.err.println("A modpack page URL or slug identifier is required");
            }
            return ExitCode.USAGE;
        }

        final ExcludeIncludesContent excludeIncludes = loadExcludeIncludes();

        final CurseForgeInstaller installer = new CurseForgeInstaller(outputDirectory, resultsFile)
            .setExcludeIncludes(excludeIncludes)
            .setForceSynchronize(forceSynchronize)
            .setForceReinstallModloader(forceReinstallModloader)
            .setIgnoreMissingFiles(ignoreMissingFiles)
            .setLevelFrom(levelFrom)
            .setOverridesSkipExisting(overridesSkipExisting)
            .setOverridesExclusions(overridesExclusions)
            .setSharedFetchOptions(sharedFetchArgs.options())
            .setApiKey(apiKey)
            .setDownloadsRepo(downloadsRepo)
            .setDisableApiCaching(disableApiCaching)
            .setCacheArgs(cacheArgs);

        if (apiBaseUrl != null) {
            installer.setApiBaseUrl(apiBaseUrl);
        }

        final Path needsDownloadFile = missingModsFilename != null && !missingModsFilename.isEmpty() ?
            outputDirectory.resolve(missingModsFilename)
            : null;

        try {
            if (modpackZip != null) {
                installer.installFromModpackZip(modpackZip, slug);
            } else if (modpackManifest != null) {
                installer.installFromModpackManifest(modpackManifest, slug);
            } else {
                installer.install(slug, filenameMatcher, fileId);
            }
            if (needsDownloadFile != null) {
                Files.deleteIfExists(needsDownloadFile);
            }

            return ExitCode.OK;
        } catch (MissingModsException e) {

            final TabularOutput tabOut = buildTabularOutput(e);

            if (needsDownloadFile != null) {
                try (BufferedWriter writer = Files.newBufferedWriter(needsDownloadFile)) {
                    tabOut.output(new PrintWriter(writer));
                }
            }

            System.err.println();
            System.err.println("Some mod authors disallow automated downloads.");
            System.err.println("The following need to be manually downloaded into the repo or excluded:");
            if (needsDownloadFile != null) {
                System.err.printf("(Also written to %s)%n", needsDownloadFile);
            }
            System.err.println();
            final PrintWriter out = new PrintWriter(System.err);
            tabOut.output(out);
            out.close();

            return ExitCode.USAGE;
        }

    }

    @NotNull
    private static TabularOutput buildTabularOutput(MissingModsException e) {
        final TabularOutput tabOut = new TabularOutput('=', "  ",
            "Mod", "Version Name", "Filename", "Download page"
        )
            .limitColumnWidth(0, 20)
            .trimSuffix(1, ".jar");
        for (PathWithInfo info : e.getNeedsDownload()) {
            tabOut.addRow(
                info.getModInfo().getName(),
                info.getCurseForgeFile().getDisplayName(),
                info.getCurseForgeFile().getFileName(),
                info.getModInfo().getLinks().getWebsiteUrl() + "/download/" + info.getCurseForgeFile().getId()
            );
        }
        return tabOut;
    }

    private ExcludeIncludesContent loadExcludeIncludes() throws IOException {
        final ExcludeIncludesContent fromFile =
            excludeIncludeArgs.excludeIncludeFile != null ? loadExcludeIncludesFile()
                : null;

        if (excludeIncludeArgs.listed != null) {
            if (fromFile != null) {
                // Merge listed global ones with content from file

                return new ExcludeIncludesContent()
                    .setGlobalExcludes(
                        mergeSets(
                            excludeIncludeArgs.listed.excludedMods,
                            fromFile.getGlobalExcludes()
                        ))
                    .setGlobalForceIncludes(
                        mergeSets(
                            excludeIncludeArgs.listed.forceIncludeMods,
                            fromFile.getGlobalForceIncludes()
                        )
                    )
                    .setModpacks(fromFile.getModpacks());
            }
            else {
                return new ExcludeIncludesContent()
                    .setGlobalExcludes(excludeIncludeArgs.listed.excludedMods)
                    .setGlobalForceIncludes(excludeIncludeArgs.listed.forceIncludeMods);

            }
        }
        else {
            return fromFile;
        }
    }

    private Set<String> mergeSets(Set<String> s1, Set<String> s2) {
        if (s1 == null) {
            return s2;
        }
        if (s2 == null) {
            return s1;
        }
        return Stream.concat(
            s1.stream(),
            s2.stream()
        ).collect(Collectors.toSet());
    }

    private ExcludeIncludesContent loadExcludeIncludesFile() throws IOException {
        if (excludeIncludeArgs.excludeIncludeFile.getPath() != null) {
            return ObjectMappers.defaultMapper()
                .readValue(excludeIncludeArgs.excludeIncludeFile.getPath().toFile(),
                    ExcludeIncludesContent.class
                );
        }
        else {
            return fetch(excludeIncludeArgs.excludeIncludeFile.getUri())
                .toObject(ExcludeIncludesContent.class)
                .acceptContentTypes(null)
                .execute();
        }

    }

}
