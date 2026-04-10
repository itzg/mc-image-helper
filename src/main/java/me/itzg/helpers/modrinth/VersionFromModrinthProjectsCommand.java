package me.itzg.helpers.modrinth;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.model.VersionType;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import reactor.core.publisher.Flux;

@Command(name = "version-from-modrinth-projects", description = "Finds a compatible Minecraft version across given Modrinth projects")
@Slf4j
public class VersionFromModrinthProjectsCommand implements Callable<Integer> {

    @Option(
        names = "--projects",
        description = "Project ID or Slug. Can be <project ID>|<slug>,"
            + " <loader>:<project ID>|<slug>,"
            + " <loader>:<project ID>|<slug>:<version ID|version number|release type>,"
            + " '@'<filename with ref per line (supports # comments)>"
            + "%nAppend '?' to mark a project as optional (excluded from version resolution)."
            + "%nExamples: fabric-api, fabric:fabric-api, fabric:fabric-api:0.76.1+1.19.2,"
            + " datapack:terralith, pl3xmap?, @/path/to/modrinth-mods.txt"
            + "%nValid release types: release, beta, alpha"
            + "%nValid loaders: fabric, forge, paper, datapack, etc.",
        split = SPLIT_COMMA_NL,
        splitSynopsisLabel = SPLIT_SYNOPSIS_COMMA_NL,
        paramLabel = "[loader:]id|slug[?][:version]",
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
        // Parse all refs and separate optional from required
        final List<ProjectRef> allRefs = projectRefs.stream()
            .map(ProjectRef::parse)
            .collect(Collectors.toList());

        final List<ProjectRef> requiredRefs = allRefs.stream()
            .filter(ref -> !ref.isOptional())
            .collect(Collectors.toList());

        // Use required projects only for version resolution.
        // If all projects are optional, fall back to using all of them.
        final List<ProjectRef> effectiveRefs;
        if (requiredRefs.isEmpty()) {
            // warning logs go to stderr -- won't pollute stdout
            log.warn("All projects are marked as optional — using all for version resolution");
            effectiveRefs = allRefs;
        } else {
            if (requiredRefs.size() < allRefs.size()) {
                final long optionalCount = allRefs.size() - requiredRefs.size();
                // debug logs go to stderr -- won't pollute stdout
                log.debug("Excluding {} optional project(s) from Minecraft version resolution", optionalCount);
            }
            effectiveRefs = requiredRefs;
        }

        final List<List<String>> allGameVersions = Flux.fromIterable(effectiveRefs)
            .flatMap(projectRef -> {
                final Loader loader = projectRef.getLoader() != null ? projectRef.getLoader() : defaultLoader;
                final VersionType allowedVersionType = projectRef.hasVersionType()
                    ? projectRef.getVersionType()
                    : defaultVersionType;

                return modrinthApiClient.resolveProjectGameVersions(projectRef, loader, null, allowedVersionType);
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
