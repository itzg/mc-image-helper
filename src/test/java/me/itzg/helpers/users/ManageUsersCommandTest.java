package me.itzg.helpers.users;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;
import static uk.org.webcompere.modelassert.json.condition.ConditionList.conditions;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@WireMockTest
class ManageUsersCommandTest {

    private static final String USER1_ID = "3f5f20286a85445fa7b46100e70c2b3a";
    private static final String USER1_UUID = "3f5f2028-6a85-445f-a7b4-6100e70c2b3a";
    private static final String USER2_ID = "5e5a1b2294b14f5892466062597e4c91";
    private static final String USER2_UUID = "5e5a1b22-94b1-4f58-9246-6062597e4c91";

    @TempDir
    Path tempDir;

    @Nested
    public class whitelist {
        @Test
        void givenNames(WireMockRuntimeInfo wmInfo) {
            setupUserStubs();

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    "user1", "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            final Path expectedFile = tempDir.resolve("whitelist.json");

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("user2")
                            .at("/uuid").hasValue(USER2_UUID)
                        )
                );
        }

        @Test
        void givenNamesAndAllExist(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("whitelist.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\"},"
                        + "{\"name\":\"user2\",\"uuid\":\"" + USER2_UUID + "\"}"
                        + "]"
                )
            );

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    "user1", "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("user2")
                            .at("/uuid").hasValue(USER2_UUID)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }

        @Test
        void allRemoved(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("whitelist.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\"},"
                        + "{\"name\":\"user2\",\"uuid\":\"" + USER2_UUID + "\"}"
                        + "]"
                )
            );

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArray()
                .hasSize(0);

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }

        @Test
        void givenNameExistsInCache(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("usercache.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "[{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID
                        + "\",\"expiresOn\":\"2023-09-13 20:14:26 +0000\"}]"
                )
            );

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    "user1"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
        }

        @Test
        void oneLessInList(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("whitelist.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\"},"
                        + "{\"name\":\"user2\",\"uuid\":\"" + USER2_UUID + "\"}"
                        + "]"
                )
            );

            // now run with user2 not included
            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    "user1"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }

        @Test
        void merge(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("whitelist.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\"}"
                        + "]"
                )
            );

            // now run with user2 not included
            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    "--existing", "MERGE",
                    "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("user2")
                            .at("/uuid").hasValue(USER2_UUID)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(1, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }

        @Test
        void skipExisting(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("whitelist.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\"}"
                        + "]"
                )
            );

            // now run with user2 not included
            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    "--existing", "SKIP",
                    "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }

        @Test
        void fromScratchGivenIdAndUuid(WireMockRuntimeInfo wmInfo) {
            final Path expectedFile = tempDir.resolve("whitelist.json");

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    USER1_UUID, USER2_ID
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            // names weren't known, just need to be present
                            .at("/name").hasValue("")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("")
                            .at("/uuid").hasValue(USER2_UUID)
                        )
                );

        }

        @Test
        void givenUuidsAndAllExist(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("whitelist.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\"},"
                        + "{\"name\":\"user2\",\"uuid\":\"" + USER2_UUID + "\"}"
                        + "]"
                )
            );

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_WHITELIST",
                    "--output-directory", tempDir.toString(),
                    // mix of ID and UUIDs
                    USER1_ID, USER2_UUID
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            // names should be retained from existing file
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("user2")
                            .at("/uuid").hasValue(USER2_UUID)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }
    }

    @Nested
    public class whitelistOrOpsText {

        @ParameterizedTest
        @EnumSource(Type.class)
        void startingWithoutFile(Type type) {
            final Path expectedFile = tempDir.resolve( type == Type.JAVA_WHITELIST ? "white-list.txt" : "ops.txt");
            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--version", "1.7.1",
                    "user1", "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists()
                .content()
                .isEqualToNormalizingNewlines("user1\nuser2\n");
        }

        @ParameterizedTest
        @EnumSource(Type.class)
        void replaceFile(Type type) throws IOException {
            final Path expectedFile = tempDir.resolve( type == Type.JAVA_WHITELIST ? "white-list.txt" : "ops.txt");
            Files.write(expectedFile, Collections.singletonList("user1"));

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--version", "1.7.1",
                    "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists()
                .content()
                .isEqualToNormalizingNewlines("user2\n");
        }

        @ParameterizedTest
        @EnumSource(Type.class)
        void merge(Type type) throws IOException {
            final Path expectedFile = tempDir.resolve( type == Type.JAVA_WHITELIST ? "white-list.txt" : "ops.txt");
            Files.write(expectedFile, Collections.singletonList("user1"));

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--version", "1.7.1",
                    "--existing", "MERGE",
                    "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists()
                .content()
                .isEqualToNormalizingNewlines("user1\nuser2\n");
        }
    }

    @Nested
    public class ops {
        @ParameterizedTest
        @EnumSource(UserApiProvider.class)
        void givenNames(UserApiProvider apiProvider, WireMockRuntimeInfo wmInfo) {
            setupUserStubs();

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--playerdb-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", apiProvider.name(),
                    "--type", "JAVA_OPS",
                    "--output-directory", tempDir.toString(),
                    "user1", "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            final Path expectedFile = tempDir.resolve("ops.json");

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                            .at("/level").hasValue(4)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("user2")
                            .at("/uuid").hasValue(USER2_UUID)
                            .at("/level").hasValue(4)
                        )
                );
        }

        @ParameterizedTest
        @EnumSource(UserApiProvider.class)
        void givenNamesWithExtraSpace(UserApiProvider apiProvider, WireMockRuntimeInfo wmInfo) {
            setupUserStubs();

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--playerdb-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", apiProvider.name(),
                    "--type", "JAVA_OPS",
                    "--output-directory", tempDir.toString(),
                    " user1"
                );

            assertThat(exitCode).isEqualTo(0);

            final Path expectedFile = tempDir.resolve("ops.json");

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                            .at("/level").hasValue(4)
                        )
                );
        }

        @Test
        void givenNamesAndAllExist(WireMockRuntimeInfo wmInfo) throws IOException {
            setupUserStubs();

            final Path expectedFile = tempDir.resolve("ops.json");
            Files.write(expectedFile,
                Collections.singletonList(
                    "["
                        // make sure the bypassesPlayerLimit is retained
                        + "{\"name\":\"user1\",\"uuid\":\"" + USER1_UUID + "\",\"level\": 4,\"bypassesPlayerLimit\": true},"
                        + "{\"name\":\"user2\",\"uuid\":\"" + USER2_UUID + "\",\"level\": 4,\"bypassesPlayerLimit\": false}"
                        + "]"
                )
            );

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", "JAVA_OPS",
                    "--output-directory", tempDir.toString(),
                    "user1", "user2"
                );

            assertThat(exitCode).isEqualTo(0);

            assertThat(expectedFile).exists();

            assertJson(expectedFile)
                .isArrayContainingExactlyInAnyOrder(
                    conditions()
                        .satisfies(conditions()
                            .at("/name").hasValue("user1")
                            .at("/uuid").hasValue(USER1_UUID)
                            .at("/level").hasValue(4)
                            .at("/bypassesPlayerLimit").hasValue(true)
                        )
                        .satisfies(conditions()
                            .at("/name").hasValue("user2")
                            .at("/uuid").hasValue(USER2_UUID)
                            .at("/level").hasValue(4)
                            .at("/bypassesPlayerLimit").hasValue(false)
                        )
                );

            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user1")));
            verify(0, getRequestedFor(urlEqualTo("/users/profiles/minecraft/user2")));
        }

    }

    @Nested
    class file {

        @ParameterizedTest
        @EnumSource(Type.class)
        void localFile(Type type, WireMockRuntimeInfo wmInfo) throws IOException {

            @Language("JSON") final String sourceContent = "[{\"name\": \"testing\", \"uuid\": \"dec16109-30d4-4425-bcc1-5222255eb6b0\"}]";

            final Path inputFile = Files.write(tempDir.resolve("input.json"),
                Collections.singletonList(sourceContent));

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--input-is-file",
                    inputFile.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            final Path expectedFile = tempDir.resolve(
                type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
            );

            assertThat(expectedFile).hasContent(sourceContent);
        }

        @ParameterizedTest
        @EnumSource(Type.class)
        void localFileDestinationExists(Type type, WireMockRuntimeInfo wmInfo) throws IOException {

            final Path expectedFile = tempDir.resolve(
                type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
            );

            Files.write(expectedFile, Collections.singletonList("[]"));

            @Language("JSON") final String sourceContent = "[{\"name\": \"testing\", \"uuid\": \"dec16109-30d4-4425-bcc1-5222255eb6b0\"}]";

            final Path inputFile = Files.write(tempDir.resolve("input.json"),
                Collections.singletonList(sourceContent));

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--input-is-file",
                    inputFile.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(expectedFile).hasContent(sourceContent);
        }

        @ParameterizedTest
        @EnumSource(Type.class)
        void localFileDestinationExistsButSkip(Type type, WireMockRuntimeInfo wmInfo) throws IOException {

            final Path expectedFile = tempDir.resolve(
                type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
            );

            final String oldContent = "[{\"name\": \"other\", \"uuid\": \"c667495b-5ee0-479f-bf55-a71a8d137cb1\"}]";
            Files.write(expectedFile, Collections.singletonList(oldContent));

            @Language("JSON") final String newContent = "[{\"name\": \"testing\", \"uuid\": \"dec16109-30d4-4425-bcc1-5222255eb6b0\"}]";

            final Path inputFile = Files.write(tempDir.resolve("input.json"),
                Collections.singletonList(newContent));

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--input-is-file",
                    "--existing", "SKIP",
                    inputFile.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(expectedFile).hasContent(oldContent);
        }

        @ParameterizedTest
        @EnumSource(Type.class)
        void remoteFile(Type type, WireMockRuntimeInfo wmInfo) {

            @Language("JSON") final String sourceContent = "[{\"name\": \"testing\", \"uuid\": \"dec16109-30d4-4425-bcc1-5222255eb6b0\"}]";

            stubFor(get("/source.json")
                .willReturn(aResponse()
                    .withBody(sourceContent)
                )
            );

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", type.name(),
                    "--output-directory", tempDir.toString(),
                    "--input-is-file",
                    wmInfo.getHttpBaseUrl() + "/source.json"
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            final Path expectedFile = tempDir.resolve(
                type == Type.JAVA_OPS ? "ops.json" : "whitelist.json"
            );

            assertThat(expectedFile).hasContent(sourceContent);
        }

        @Test
        void handlesSameInputFileAsOutput(WireMockRuntimeInfo wmInfo) throws IOException {
            @Language("JSON") final String sourceContent = "[{\"name\": \"testing\", \"uuid\": \"dec16109-30d4-4425-bcc1-5222255eb6b0\"}]";

            final Path inputFile = Files.write(tempDir.resolve("ops.json"),
                Collections.singletonList(sourceContent));

            final int exitCode = new CommandLine(
                new ManageUsersCommand()
            )
                .execute(
                    "--mojang-api-base-url", wmInfo.getHttpBaseUrl(),
                    "--user-api-provider", "mojang",
                    "--type", Type.JAVA_OPS.name(),
                    "--output-directory", tempDir.toString(),
                    "--input-is-file",
                    inputFile.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        }
    }

    private static void setupUserStubs() {
        stubMojangUser("user1", USER1_ID);
        stubMojangUser("user2", USER2_ID);

        stubPlayerdbUser("user1", USER1_UUID, USER1_ID);
        stubPlayerdbUser("user2", USER2_UUID, USER2_ID);
    }

    private static void stubMojangUser(String username, String id) {
        stubFor(get("/users/profiles/minecraft/"
            + username)
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\n"
                        + "  \"id\": \"" + id + "\",\n"
                        + "  \"name\": \""
                        + username
                        + "\"\n"
                        + "}")
            )
        );
    }

    private static void stubPlayerdbUser(String username, String uuid, String id) {
        stubFor(get("/api/player/minecraft/"
            + username)
            .willReturn(
                aResponse()
                    .withTransformers("response-template")
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\n"
                        + "  \"code\": \"player.found\",\n"
                        + "  \"message\": \"Successfully found player by given ID.\",\n"
                        + "  \"data\": {\n"
                        + "    \"player\": {\n"
                        + "      \"meta\": {\n"
                        + "        \"cached_at\": 1707337378\n"
                        + "      },\n"
                        + "      \"username\": \"" + username + "\",\n"
                        + "      \"id\": \"" + uuid + "\",\n"
                        + "      \"raw_id\": \"" + id + "\",\n"
                        + "      \"properties\": [\n"
                        + "        {\n"
                        + "          \"name\": \"textures\",\n"
                        + "          \"value\": \"ewogICJ0aW1lc3RhbXAiIDogMTcwNzI5NjczMDM3NywKICAicHJvZmlsZUlkIiA6ICIwNTkxMDMwODIyOWQ0NmJiYTQ2NmU1NTc0ZGJhNDkwYyIsCiAgInByb2ZpbGVOYW1lIiA6ICJVc2VyMSIsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9kOThkZGM5ZjM3NDA4YmNjMTk5YTNhYzA2Mzc3NGU1ODcwZjc3Y2NjYjJkNGMwODRmMjc3YTkxYTM3ZDU1YzIyIgogICAgfQogIH0KfQ==\"\n"
                        + "        }\n"
                        + "      ],\n"
                        + "      \"name_history\": [\n"
                        + "\n"
                        + "      ]\n"
                        + "    }\n"
                        + "  },\n"
                        + "  \"success\": true\n"
                        + "}")
            )
        );
    }
}