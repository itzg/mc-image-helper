package me.itzg.helpers.curseforge;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.itzg.helpers.files.ResultsFileWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "install-curseforge")
public class InstallCurseForgeCommand implements Callable<Integer> {
    @Option(names = {"--help","-h"}, usageHelp = true)
    boolean help;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--modpack-page-url", paramLabel = "URL",
        description = "URL of a modpack page such as "
        + "https://www.curseforge.com/minecraft/modpacks/all-the-mods-8"
        + "or a file page https://www.curseforge.com/minecraft/modpacks/all-the-mods-8/files/4248390")
    String pageUrl;

    @Option(names = "--slug", description = "The short-URL identifier")
    String slug;

    @Option(names = "--file-id")
    Integer fileId;

    @Option(names = "--exclude-mods", paramLabel = "PROJECT_ID",
        split = "\\s+|,", splitSynopsisLabel = "Whitespace or commas",
        description = "For mods that need to be excluded from server deployments, such as those that don't label as client"
    )
    Set<Integer> excludedModIds;

    @Option(names = "--filename-matcher", paramLabel = "STR",
        description = "Substring to select specific modpack filename")
    String filenameMatcher;

    @Option(names = "--force-reinstall")
    boolean forceReinstall;

    @Option(names = "--parallel-downloads", defaultValue = "4",
        description = "Default: ${DEFAULT-VALUE}"
    )
    int parallelDownloads;

    private static final Pattern PAGE_URL_PATTERN = Pattern.compile(
        "https://www.curseforge.com/minecraft/modpacks/(?<slug>.+?)(/files(/(?<fileId>\\d+)?)?)?");

    @Override
    public Integer call() throws Exception {
        // https://www.curseforge.com/minecraft/modpacks/all-the-mods-8/files
        // https://www.curseforge.com/minecraft/modpacks/all-the-mods-8/files/4248390

        if (pageUrl != null) {
            final Matcher m = PAGE_URL_PATTERN.matcher(pageUrl);
            if (m.matches()) {
                slug = m.group("slug");
                final String fileIdStr = m.group("fileId");
                if (fileIdStr != null) {
                    fileId = Integer.parseInt(fileIdStr);
                }
            }
            else {
                System.err.println("Unexpected URL structure: "+pageUrl);
                return ExitCode.USAGE;
            }
        }

        if (slug == null) {
            System.err.println("A modpack page URL or slug identifier is required");
            return ExitCode.USAGE;
        }

        final CurseForgeInstaller installer = new CurseForgeInstaller(outputDirectory, resultsFile)
            .setForceReinstall(forceReinstall)
            .setParallelism(parallelDownloads);
        installer.install(slug, filenameMatcher, fileId, excludedModIds);

        return ExitCode.OK;
    }
}
