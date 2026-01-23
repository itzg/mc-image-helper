package me.itzg.helpers.modrinth;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;

@Command(name = "version-from-modrinth-projects", description = "Finds a compatible Minecraft version across given Modrinth projects")
public class VersionFromModrinthProjectsCommand implements Callable<Integer> {

    @Option(
        names = "--projects",
        description = "Project ID or Slug. Can be <project ID>|<slug>,"
            + " <loader>:<project ID>|<slug>,"
            + " <loader>:<project ID>|<slug>:<version ID|version number|release type>,"
            + " '@'<filename with ref per line (supports # comments)>"
            + "%nExamples: fabric-api, fabric:fabric-api, fabric:fabric-api:0.76.1+1.19.2,"
            + " datapack:terralith, @/path/to/modrinth-mods.txt"
            + "%nValid release types: release, beta, alpha"
            + "%nValid loaders: fabric, forge, paper, datapack, etc.",
        split = SPLIT_COMMA_NL,
        splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "[loader:]id|slug[:version]",
        // at least one is required
        arity = "1..*"
    )
    List<String> projects;

    @Option(names = "--loader", description = "Valid values: ${COMPLETION-CANDIDATES}")
    Loader loader;

    @Option(names = "--allowed-version-type", defaultValue = "release", description = "Valid values: ${COMPLETION-CANDIDATES}")
    VersionType defaultVersionType;

    @Option(names = "--api-base-url", defaultValue = "${env:MODRINTH_API_BASE_URL:-https://api.modrinth.com}",
        description = "Default: ${DEFAULT-VALUE}"
    )
    String baseUrl;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws Exception {
        try (ModrinthApiClient modrinthApiClient = new ModrinthApiClient(baseUrl, "modrinth", sharedFetchArgs.options())) {
            final String version = versionFromProjects(modrinthApiClient, projects, loader, defaultVersionType);

            if (version != null) {
                System.out.println(version);
                return ExitCode.OK;
            }
            else {
                System.err.println("Unable to find a compatible Minecraft version across given projects");
                return ExitCode.SOFTWARE;
            }
        }
    }

    static String versionFromProjects(ModrinthApiClient modrinthApiClient, List<String> projectRefs, Loader defaultLoader, VersionType defaultVersionType) {
        final List<List<String>> allGameVersions = Flux.fromIterable(projectRefs)
            .map(ProjectRef::parse)
            .flatMap(projectRef -> {
                final Loader loader = projectRef.getLoader() != null ? projectRef.getLoader() : defaultLoader;

                VersionType allowedVersionType = projectRef.hasVersionType()
                    ? projectRef.getVersionType()
                    : defaultVersionType;

                String versionNumberFilter = null;
                if (!projectRef.hasVersionType() && projectRef.hasVersionName()) {
                    final String raw = projectRef.getVersionNumber();
                    try {
                        allowedVersionType = VersionType.valueOf(raw.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        versionNumberFilter = raw;
                    }
                }

                final VersionType finalAllowedVersionType = allowedVersionType;
                final String finalVersionNumberFilter = versionNumberFilter;
                return modrinthApiClient.getVersionsForProject(projectRef.getIdOrSlug(), loader, null)
                    .map(versions -> versions.stream()
                        .sorted(Comparator.comparing(Version::getDatePublished))
                        .filter(v -> v.getVersionType().sufficientFor(finalAllowedVersionType))
                        .filter(v -> finalVersionNumberFilter == null
                            || finalVersionNumberFilter.equals(v.getVersionNumber())
                            || finalVersionNumberFilter.equals(v.getName()))
                        .flatMap(v -> v.getGameVersions().stream())
                        .distinct()
                        .collect(Collectors.toList())
                    );
            })
            .collectList()
            .block();

        if (allGameVersions != null) {
            return processGameVersions(allGameVersions);
        }
        else {
            throw new GenericException("Unable to retrieve game versions for projects " + projectRefs);
        }
    }

    static String processGameVersions(List<List<String>> allGameVersions) {
        final Map<String, Integer> gameVersionCounts = new HashMap<>();

        final int projectCount = allGameVersions.size();

        // positions will start at the first usable position at end of each list and decrement
        // and will become negative when finished traversing
        final int[] positions = new int[projectCount];
        for (int i = 0; i < projectCount; i++) {
            positions[i] = allGameVersions.get(i).size() - 1;
        }

        while (Arrays.stream(positions)
            // while any position is still usable
            .anyMatch(p -> p >= 0)
        ) {
            for (int i = 0; i < projectCount; i++) {
                // still usable?
                if (positions[i] >= 0) {
                    final int position = positions[i]--;
                    final String version = allGameVersions.get(i).get(position);
                    final Integer result = gameVersionCounts.compute(version, (k, count) -> count == null ? 1 : count + 1);
                    // did this version slot indicate match for all?
                    if (result == projectCount) {
                        return version;
                    }
                }
            }
        }

        return null;
    }
}