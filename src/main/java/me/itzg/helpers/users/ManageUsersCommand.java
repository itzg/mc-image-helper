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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.http.Uris;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.users.ext.MojangProfile;
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

    private static final Pattern ID_OR_UUID = Pattern.compile("(?<nonDashed>[a-f0-9]{32})"
        + "|(?<dashed>[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})");
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

    @Option(names = {"-a", "--append", "--append-only"}, description = "Do not remove any existing users even when not mentioned in input parameters")
    boolean appendOnly;

    @Option(names = "--version", description = "Minecraft game version. If not provided, assumes JSON format")
    String version;

    @Option(names = {"-f", "--input-is-file"})
    boolean inputIsFile;

    @Option(names = "--offline", description = "Disable API conversion of usernames to UUID")
    boolean offline;

    @Option(names = "--mojang-api-base-url", defaultValue = "${env:MOJANG_API_BASE_URL:-https://api.mojang.com/}")
    String mojangApiBaseUrl;

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

            final Set<String> users = loadExistingTextUserList(resultFile);

            users.addAll(inputs);

            log.debug("Writing users list to {}: {}", resultFile, users);
            Files.write(resultFile, users);
        }
        else {
            final Path resultFile = outputDirectory.resolve(
                type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
            );

            objectMapper.writeValue(resultFile.toFile(),
                reconcile(sharedFetch, inputs,
                    loadExistingJavaJson(resultFile)
                )
            );
        }
    }

    private List<? extends JavaUser> reconcile(SharedFetch sharedFetch, List<String> inputs, List<? extends JavaUser> existing)
        throws IOException {

        final List<JavaUser> reconciled;
        if (appendOnly) {
            reconciled = new ArrayList<>(existing);
        }
        else {
            reconciled = new ArrayList<>(inputs.size());
        }

        for (final String input : inputs) {
            final JavaUser resolvedUser = resolveJavaUserId(sharedFetch, existing, input.trim());

            if (!appendOnly || !containsUserByUuid(reconciled, resolvedUser.getUuid())) {
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

    private JavaUser resolveJavaUserId(SharedFetch sharedFetch, List<? extends JavaUser> existing, String input)
        throws IOException {
        final Matcher uuidMatcher = ID_OR_UUID.matcher(input);
        if (uuidMatcher.matches()) {
            final String dashed = uuidMatcher.group("dashed");
            final String uuid = dashed != null ? dashed :
                addDashesToId(uuidMatcher.group("nonDashed"));

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
        }

        // ...or username
        for (final JavaUser existingUser : existing) {
            if (existingUser.getName().equalsIgnoreCase(input)) {
                log.debug("Resolved '{}' from existing user entry by name: {}", input, existingUser);
                return existingUser;
            }
        }

        final Path userCacheFile = outputDirectory.resolve("usercache.json");
        if (Files.exists(userCacheFile)) {
            final List<JavaUser> userCache = objectMapper.readValue(userCacheFile.toFile(), LIST_OF_JAVA_USER);
            for (final JavaUser existingUser : userCache) {
                if (existingUser.getName().equalsIgnoreCase(input)) {
                    log.debug("Resolved '{}' from user cache by name: {}", input, existingUser);
                    return existingUser;
                }
            }
        }

        if (offline) {
            throw new InvalidParameterException("Unable to resolve username while offline: " + input);
        }

        return resolveUserFromApi(sharedFetch, input);
    }

    private JavaUser resolveUserFromApi(SharedFetch sharedFetch, String input) {
        log.debug("Resolving user={} from Mojang API at {}", input, mojangApiBaseUrl);
        final UriBuilder uriBuilder = UriBuilder.withBaseUrl(mojangApiBaseUrl);
        final MojangProfile profile = sharedFetch.fetch(
                uriBuilder.resolve("/users/profiles/minecraft/{username}", input)
            )
            .toObject(MojangProfile.class)
            .assemble()
            .onErrorMap(e -> {
                if (e instanceof FailedRequestException) {
                    if (((FailedRequestException) e).getStatusCode() == 404) {
                        return new InvalidParameterException("Could not resolve username from Mojang: " + input);
                    }
                }
                return e;
            })
            .block();

        if (profile == null) {
            throw new GenericException("Profile was not available from Mojang for " + input);
        }

        log.debug("Resolved '{}' from Mojang profile lookup: {}", input, profile);
        return JavaUser.builder()
            .name(profile.getName())
            .uuid(addDashesToId(profile.getId()))
            .build();
    }

    private static String addDashesToId(String nonDashed) {
        if (nonDashed.length() != 32) {
            throw new IllegalArgumentException("Input needs to be 32 characters: " + nonDashed);
        }

        return String.join("-",
            nonDashed.substring(0, 8),
            nonDashed.substring(8, 12),
            nonDashed.substring(12, 16),
            nonDashed.substring(16, 20),
            nonDashed.substring(20, 32)
        );
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
        if (appendOnly && Files.exists(resultFile)) {
            log.debug("Loading existing users from {}", resultFile);
            return new HashSet<>(Files.readAllLines(resultFile));
        }

        return new HashSet<>();
    }

    private void verifyNotUuids(List<String> inputs) {
        for (final String input : inputs) {
            final Matcher m = ID_OR_UUID.matcher(input);
            if (m.matches()) {
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
            log.debug("Copying from {} to {}", filePathUrl, outputFile);

            Files.copy(Paths.get(filePathUrl), outputFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean usesTextUserList() {
        return version != null && new ComparableVersion(version).compareTo(MIN_VERSION_USES_JSON) < 0;
    }

}
