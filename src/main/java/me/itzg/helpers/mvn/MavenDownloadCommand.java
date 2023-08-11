package me.itzg.helpers.mvn;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "maven-download", description = "Downloads a maven artifact from a Maven repository")
@Slf4j
public class MavenDownloadCommand implements Callable<Integer> {

    public static final String MAVEN_CACHE_SUBDIR = ".maven";
    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    @Option(names = "--output-directory", defaultValue = ".")
    Path outputDirectory;

    @Option(names = {"--maven-repo", "-r"}, defaultValue = "https://repo1.maven.org/maven2/", required = true)
    URI mavenRepo;

    @Option(names = {"--group", "-g"}, required = true)
    String group;

    @Option(names = {"--artifact","--module","-a","-m"}, required = true)
    String artifact;

    @Option(names = {"--version", "-v"}, defaultValue = "release", required = true,
        description = "A specific version, 'release', or 'latest'" +
            "%nDefault: ${DEFAULT-VALUE}"
    )
    String version;

    @Option(names = "--packaging", defaultValue = "jar")
    String packaging;

    @Option(names = "--classifier")
    String classifier;

    @Option(names = "--print-filename", defaultValue = "true")
    boolean printFilename;

    @Option(names = "--skip-up-to-date", defaultValue = "true")
    boolean skipUpToDate;

    @Option(names = "--skip-existing", defaultValue = "false")
    boolean skipExisting;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {
        final Path result;
        try (SharedFetch sharedFetch = Fetch.sharedFetch("maven-download", sharedFetchArgs.options())) {
            final MavenRepoApi mavenRepoApi = new MavenRepoApi(mavenRepo.toString(), sharedFetch)
                .setMetadataCacheDir(outputDirectory.resolve(MAVEN_CACHE_SUBDIR))
                .setSkipExisting(skipExisting)
                .setSkipUpToDate(skipUpToDate);

            result = mavenRepoApi.fetchMetadata(group, artifact)
                .flatMap(mavenMetadata -> mavenRepoApi.download(
                    outputDirectory, group, artifact, resolveVersion(mavenMetadata), packaging, classifier
                ))
                .block();
        }

        log.debug("Downloaded artifact to {}", result);
        if (printFilename) {
            System.out.println(result);
        }

        return ExitCode.OK;
    }

    private String resolveVersion(MavenMetadata mavenMetadata) {
        log.debug("Resolving version={} given metadata={}", version, mavenMetadata);

        if (version.equalsIgnoreCase("release")) {
            final String resolved = mavenMetadata.getVersioning().getRelease();
            if (resolved == null) {
                throw new IllegalArgumentException("Release version is not declared in metadata");
            }
            return resolved;
        } else if (version.equalsIgnoreCase("latest")) {
            final String resolved = mavenMetadata.getVersioning().getLatest();
            if (resolved == null) {
                throw new IllegalArgumentException("Latest version is not declared in metadata");
            }
            return resolved;
        } else {
            if (!mavenMetadata.getVersioning().getVersion().contains(version)) {
                throw new IllegalArgumentException("Requested version does not exist");
            }
            return version;
        }
    }

}
