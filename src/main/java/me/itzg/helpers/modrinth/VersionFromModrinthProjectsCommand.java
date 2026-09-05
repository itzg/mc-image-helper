package me.itzg.helpers.modrinth;

import static me.itzg.helpers.McImageHelper.SPLIT_COMMA_NL;
import static me.itzg.helpers.McImageHelper.SPLIT_SYNOPSIS_COMMA_NL;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
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

        if (projects == null || projects.isEmpty()) {
            throw new InvalidParameterException("No Modrinth projects provided, please provide at least one Modrinth project");
        }

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

    static String versionFromProjects(ModrinthApiClient modrinthApiClient, List<String> projectRefs, Loader defaultLoader, VersionType defaultVersionType) throws InvalidParameterException {
        // Parse all refs and separate optional from required
        final List<ProjectRef> allRefs = projectRefs.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(ProjectRef::parse)
            .distinct()
            .collect(Collectors.toList());

        if (allRefs.isEmpty()) {
            throw new InvalidParameterException("No Modrinth projects parsed successfully, please ensure projects follow \"<loader>:<project ID>|<slug>\" and are delimited by commas");
        }

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
            .flatMapSequential(projectRef -> {
                final Loader loader = projectRef.getLoader() != null ? projectRef.getLoader() : defaultLoader;
                final VersionType allowedVersionType = projectRef.hasVersionType()
                    ? projectRef.getVersionType()
                    : defaultVersionType;

                return modrinthApiClient.resolveProjectGameVersions(projectRef, loader, null, allowedVersionType)
                    .onErrorMap(error -> new GenericException("Failed to resolve project version for " + projectRef.getIdOrSlug(), error));
            })
            .collectList()
            .block();

        if (allGameVersions != null) {
            return processGameVersions(allGameVersions, effectiveRefs);
        }
        else {
            throw new GenericException("Unable to retrieve game versions for projects " + projectRefs);
        }
    }

    static String processGameVersions(List<List<String>> allGameVersions, List<ProjectRef> projects) {

        final Map<String, int[]> gameVersionPositions = new HashMap<>();
        final Set<String> loggedBlockedVersions = new HashSet<>();

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

                    final int[] projectPositions = gameVersionPositions.computeIfAbsent(version, ignored -> {
                        final int[] result = new int[projectCount];
                        Arrays.fill(result, -1);
                        return result;
                    });

                    // Prevent duplicate entries from the same project counting twice.
                    if (projectPositions[i] < 0) {
                        projectPositions[i] = position;
                    }

                    // did this version slot indicate match for all?
                    if (Arrays.stream(projectPositions).allMatch(projectPosition -> projectPosition >= 0)) {
                        return version;
                    }

                    if (log.isDebugEnabled() && !loggedBlockedVersions.contains(version)) {
                        final List<Integer> blockingProjects = IntStream.range(0, projectCount)
                            .filter(projectIndex -> !allGameVersions.get(projectIndex).contains(version))
                            .boxed()
                            .collect(Collectors.toList());

                        if (!blockingProjects.isEmpty()) {
                            loggedBlockedVersions.add(version);
                            log.debug("Minecraft version {} is blocked by projects {}", version, 
                                    blockingProjects.stream()
                                    .map(projects::get)
                                    .map(p -> p.getIdOrSlug())
                                    .collect(Collectors.toList()));
                        }
                    }
                }
            }
        }

        return null;
    }
}