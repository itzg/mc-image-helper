package me.itzg.helpers.users;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.users.model.JavaOp;
import me.itzg.helpers.users.model.JavaUser;
import org.apache.maven.artifact.versioning.ComparableVersion;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "manage-users")
@Slf4j
public class ManageUsersCommand implements Callable<Integer> {

    private static final TypeReference<List<JavaOp>> LIST_OF_JAVA_OP = new TypeReference<List<JavaOp>>() {
    };
    private static final TypeReference<List<JavaUser>> LIST_OF_JAVA_USER = new TypeReference<List<JavaUser>>() {
    };
    private static final ComparableVersion MIN_VERSION_USES_JSON = new ComparableVersion("1.7.3");

    @SuppressWarnings("unused")
    @Option(names = {"--help", "-h"}, usageHelp = true)
    boolean help;

    @Option(names = "--output-directory", defaultValue = ".")
    Path outputDirectory;

    @Option(names = {"--type", "-t"}, required = true, description = "Allowed: ${COMPLETION-CANDIDATES}")
    Type type;

    @Option(names = "--existing", defaultValue = "SYNCHRONIZE",
        description = "Select the behavior when the resulting file already exists\nAllowed: ${COMPLETION-CANDIDATES}")
    ExistingFileBehavior existingFileBehavior;

    @Option(names = "--version", description = "Minecraft game version. If not provided, assumes JSON format")
    String version;

    @Option(names = {"-f", "--input-is-file"})
    boolean inputIsFile;

    @Option(names = "--mojang-api-base-url", defaultValue = "${env:MOJANG_API_BASE_URL:-https://api.mojang.com}")
    String mojangApiBaseUrl;

    @Option(names = "--playerdb-api-base-url", defaultValue = "${env:PLAYERDB_API_BASE_URL:-https://playerdb.co}")
    String playerdbApiBaseUrl;

    @Option(names = "--user-api-provider", defaultValue = "${env:USER_API_PROVIDER:-playerdb}",
        description = "Allowed: ${COMPLETION-CANDIDATES}"
    )
    UserApiProvider userApiProvider;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Parameters(split = ",", paramLabel = "INPUT", description = "One or more Mojang usernames, UUID, or ID (UUID without dashes);"
        + " however, when offline, only UUID/IDs can be provided."
        + "%nWhen input is a file, only one local file path or URL can be provided")
    List<String> inputs = Collections.emptyList();

    final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

    @Override
    public Integer call() throws Exception {
        try (SharedFetch sharedFetch = Fetch.sharedFetch("manage-users", sharedFetchArgs.options())) {

            if (inputIsFile) {
                if (inputs.size() != 1) {
                    throw new InvalidParameterException("One and only one input file path/URL can be provided");
                }
                if (existingFileBehavior == ExistingFileBehavior.MERGE) {
                    throw new InvalidParameterException("Merging is not supported with file input");
                }

                processInputAsFile(sharedFetch, inputs.get(0));
            }
            else {
                processJavaUserIdList(sharedFetch, inputs);
            }

        }

        return ExitCode.OK;
    }

    private void processJavaUserIdList(SharedFetch sharedFetch, List<String> inputs) throws IOException {
        if (usesTextUserList()) {
            verifyNotUuids(inputs);

            final Path resultFile = outputDirectory.resolve(
                type == Type.JAVA_OPS ? "ops.txt" : "white-list.txt"
            );

            if (handleSkipExistingFile(resultFile)) {
                return;
            }

            final Set<String> users = loadExistingTextUserList(resultFile);

            users.addAll(inputs);

            log.debug("Writing users list to {}: {}", resultFile, users);
            Files.write(resultFile, users);
        }
        else {
            final Path resultFile = outputDirectory.resolve(
                type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
            );

            if (handleSkipExistingFile(resultFile)) {
                return;
            }

            objectMapper.writeValue(resultFile.toFile(),
                reconcile(sharedFetch, inputs,
                    loadExistingJavaJson(resultFile)
                )
            );
        }
    }

    private boolean handleSkipExistingFile(Path resultFile) {
        if (existingFileBehavior == ExistingFileBehavior.SKIP
            && Files.exists(resultFile)) {
            log.info("The file {} already exists, so no changes will be made", resultFile);
            return true;
        }
        return false;
    }

    private List<? extends JavaUser> reconcile(SharedFetch sharedFetch, List<String> inputs, List<? extends JavaUser> existing) {

        final List<JavaUser> reconciled;
        if (existingFileBehavior == ExistingFileBehavior.MERGE) {
            reconciled = new ArrayList<>(existing);
        }
        else {
            reconciled = new ArrayList<>(inputs.size());
        }

        for (final String input : inputs) {
            final JavaUser resolvedUser = resolveJavaUserId(sharedFetch, existing, input.trim());

            if (existingFileBehavior == ExistingFileBehavior.SYNCHRONIZE
                    || !containsUserByUuid(reconciled, resolvedUser.getUuid())) {
                if (type == Type.JAVA_OPS) {
                    final JavaOp resolvedOp = resolvedUser instanceof JavaOp ? ((JavaOp) resolvedUser) : null;
                    reconciled.add(JavaOp.builder()
                        .name(resolvedUser.getName())
                        .uuid(resolvedUser.getUuid())
                        .level(resolvedOp != null ? resolvedOp.getLevel() : 4)
                        .bypassesPlayerLimit(resolvedOp != null && resolvedOp.isBypassesPlayerLimit())
                        .build());
                }
                else {
                    reconciled.add(JavaUser.builder()
                        .name(resolvedUser.getName())
                        .uuid(resolvedUser.getUuid())
                        .build());
                }
            }
        }

        return reconciled;
    }

    private boolean containsUserByUuid(List<JavaUser> users, String uuid) {
        for (final JavaUser user : users) {
            if (user.getUuid().equalsIgnoreCase(uuid)) {
                return true;
            }
        }
        return false;
    }

    private JavaUser resolveJavaUserId(SharedFetch sharedFetch, List<? extends JavaUser> existing, String input) {

        return UuidQuirks.ifIdOrUuid(input)
            .map(uuid -> {
                for (final JavaUser existingUser : existing) {
                    if (existingUser.getUuid().equalsIgnoreCase(uuid)) {
                        log.debug("Resolved '{}' from existing user entry by UUID: {}", input, existingUser);
                        return existingUser;
                    }
                }

                log.debug("Resolved '{}' into new user entry", input);
                return JavaUser.builder()
                    .uuid(uuid)
                    // username needs to be present, but content doesn't matter
                    .name("")
                    .build();

            })
            .orElseGet(() -> {

                // ...or username
                for (final JavaUser existingUser : existing) {
                    if (existingUser.getName().equalsIgnoreCase(input)) {
                        log.debug("Resolved '{}' from existing user entry by name: {}", input, existingUser);
                        return existingUser;
                    }
                }

                final Path userCacheFile = outputDirectory.resolve("usercache.json");
                if (Files.exists(userCacheFile)) {
                    try {
                        final List<JavaUser> userCache = objectMapper.readValue(userCacheFile.toFile(), LIST_OF_JAVA_USER);
                        for (final JavaUser existingUser : userCache) {
                            if (existingUser.getName().equalsIgnoreCase(input)) {
                                log.debug("Resolved '{}' from user cache by name: {}", input, existingUser);
                                return existingUser;
                            }
                        }
                    } catch (IOException e) {
                        log.error("Failed to parse usercache.json", e);
                    }
                }

                final UserApi userApi;
                switch (userApiProvider) {
                    case mojang:
                        userApi = new MojangUserApi(sharedFetch, mojangApiBaseUrl);
                        break;
                    case playerdb:
                        userApi = new PlayerdbUserApi(sharedFetch, playerdbApiBaseUrl);
                        break;
                    default:
                        throw new GenericException("User API provider was not specified");
                }
                return userApi.resolveUser(input);

            });

    }

    private List<? extends JavaUser> loadExistingJavaJson(Path userFile) throws IOException {
        if (!Files.exists(userFile)) {
            return Collections.emptyList();
        }

        if (type == Type.JAVA_OPS) {
            return objectMapper.readValue(userFile.toFile(), LIST_OF_JAVA_OP);
        }
        else {
            return objectMapper.readValue(userFile.toFile(), LIST_OF_JAVA_USER);
        }
    }

    /**
     * @return mutable set of users or empty if file doesn't exist
     */
    private Set<String> loadExistingTextUserList(Path resultFile) throws IOException {
        if (existingFileBehavior == ExistingFileBehavior.MERGE && Files.exists(resultFile)) {
            log.debug("Loading existing users from {}", resultFile);
            return new HashSet<>(Files.readAllLines(resultFile));
        }

        return new HashSet<>();
    }

    private void verifyNotUuids(List<String> inputs) {
        for (final String input : inputs) {
            if (UuidQuirks.isIdOrUuid(input)) {
                throw new InvalidParameterException("UUID cannot be provided: " + input);
            }
        }
    }

    private void processInputAsFile(SharedFetch sharedFetch, String filePathUrl) throws IOException {
        final Path outputFile = outputDirectory.resolve(
            usesTextUserList() ?
                (type == Type.JAVA_OPS ? "ops.txt" : "whitelist.txt")
                : type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
        );

        if (handleSkipExistingFile(outputFile)) {
            return;
        }

        if (Uris.isUri(filePathUrl)) {
            log.debug("Downloading from {} to {}", filePathUrl, outputFile);

            try {
                sharedFetch.fetch(new URI(filePathUrl))
                    .toFile(outputFile)
                    .execute();
            } catch (URISyntaxException e) {
                throw new InvalidParameterException("Given input is not a fully valid URL", e);
            }
        }
        else {
            final Path inputFile = Paths.get(filePathUrl);

            if (!Files.exists(outputFile) || !Files.isSameFile(inputFile, outputFile)) {
                log.debug("Copying from {} to {}", filePathUrl, outputFile);
                Files.copy(inputFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
            }
            else {
                log.warn("Attempting to synchronize input {} file onto itself: {}",
                    type, outputFile);
            }
        }
    }

    private boolean usesTextUserList() {
        return version != null && new ComparableVersion(version).compareTo(MIN_VERSION_USES_JSON) < 0;
    }
}
