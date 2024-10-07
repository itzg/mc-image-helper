package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import java.nio.file.Path;
import java.util.function.Consumer;
import me.itzg.helpers.json.ObjectMappers;
import me.itzg.helpers.modrinth.ModrinthCommand.DownloadDependencies;
import org.assertj.core.api.AbstractPathAssert;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class ModrinthCommandTest {
    final ObjectMapper objectMapper = ObjectMappers.defaultMapper();

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .usingFilesUnderClasspath("ModrinthCommandTest")
            .extensions(new ResponseTemplateTransformer(false))
        )
        .configureStaticDsl(true)
        .build();

    @Test
    void commaNewlineDelimited(@TempDir Path tempDir) {
        setupStubs();

        final int exitCode = new CommandLine(
            new ModrinthCommand()
        )
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--game-version", "1.19.2",
                "--loader", "fabric",
                "--projects", "fabric-api, \n"
                    + "cloth-config"
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertThat(tempDir.resolve("mods/fabric-api-0.76.1+1.19.2.jar")).exists();
        assertThat(tempDir.resolve("mods/cloth-config-8.3.103-fabric.jar")).exists();
    }

    @Test
    void newlineDelimited(@TempDir Path tempDir) {
        setupStubs();

        final int exitCode = new CommandLine(
            new ModrinthCommand()
        )
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--game-version", "1.19.2",
                "--loader", "fabric",
                "--projects", "fabric-api\n"
                    + "cloth-config"
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertThat(tempDir.resolve("mods/fabric-api-0.76.1+1.19.2.jar")).exists();
        assertThat(tempDir.resolve("mods/cloth-config-8.3.103-fabric.jar")).exists();
    }

    @ParameterizedTest
    @EnumSource(value = ModrinthCommand.DownloadDependencies.class)
    void downloadsOnlyRequestedDependencyTypes(ModrinthCommand.DownloadDependencies downloadDependencies, @TempDir Path tempDir) {
        final String projectId = randomAlphanumeric(6);
        final String projectSlug = randomAlphabetic(5);
        final String versionId = randomAlphanumeric(6);
        final String requiredDepProjectId = randomAlphanumeric(6);
        final String requiredVersionId = randomAlphanumeric(6);
        final String optionalDepProjectId = randomAlphanumeric(6);
        final String optionalVersionId = randomAlphanumeric(6);

        stubProjectBulkRequest(projectId, projectSlug);

        stubVersionRequest(projectId, versionId, deps -> {
            deps.addObject()
                .put("project_id", requiredDepProjectId)
                .put("dependency_type", "required");
            deps.addObject()
                .put("project_id", optionalDepProjectId)
                .put("dependency_type", "optional");
        });
        stubVersionRequest(requiredDepProjectId, requiredVersionId, deps -> {});
        stubVersionRequest(optionalDepProjectId, optionalVersionId, deps -> {});

        stubFor(get(urlPathMatching("/cdn/(.+)"))
            .willReturn(aResponse()
                .withBody("{{request.pathSegments.[1]}}")
                .withTransformers("response-template")
            )
        );

        final int exitCode = new CommandLine(
            new ModrinthCommand()
        )
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--game-version", "1.21.1",
                "--loader", "paper",
                "--projects", projectId,
                "--download-dependencies", downloadDependencies.name()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertVersionFile(tempDir, versionId).exists();
        verify(projectVersionsRequest(projectId));
        if (downloadDependencies == DownloadDependencies.REQUIRED) {
            assertVersionFile(tempDir, requiredVersionId).exists();
            assertVersionFile(tempDir, optionalVersionId).doesNotExist();
            verify(projectVersionsRequest(requiredDepProjectId));
            verify(0, projectVersionsRequest(optionalDepProjectId));
        }
        else if (downloadDependencies == DownloadDependencies.OPTIONAL) {
            assertVersionFile(tempDir, requiredVersionId).exists();
            assertVersionFile(tempDir, optionalVersionId).exists();
            verify(projectVersionsRequest(optionalDepProjectId));
        }
        else {
            assertVersionFile(tempDir, requiredVersionId).doesNotExist();
            assertVersionFile(tempDir, optionalVersionId).doesNotExist();
            verify(0, projectVersionsRequest(requiredDepProjectId));
            verify(0, projectVersionsRequest(optionalDepProjectId));
        }
    }

    @Test
    void errorWhenNoApplicableVersion(@TempDir Path tempDir) {
        stubFor(
            get(urlPathMatching("/v2/projects"))
                .withQueryParam("ids", equalTo("[\"geyser\"]"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("bulk-geyser.json")
                )
        );
        stubFor(
            get(urlPathMatching("/v2/project/wKkoqHrH/version"))
                .withQueryParam("loaders", equalTo("[\"fabric\"]"))
                .withQueryParam("game_versions", equalTo("[\"1.20.4\"]"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("project-geyser-only-betas.json")
                )
        );

        final int exitCode = new CommandLine(
            new ModrinthCommand()
        )
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--game-version", "1.20.4",
                "--loader", "fabric",
                "--projects", "geyser"
            );

        assertThat(exitCode).isNotEqualTo(ExitCode.OK);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void handlesDatapacksSpecificVersion(boolean absoluteWorldDir, @TempDir Path tempDir) {
        final String projectId = randomAlphanumeric(6);
        final String projectSlug = randomAlphabetic(5);
        final String versionId = randomAlphanumeric(8 /*versionId's must have len 8*/);
        final String worldDir = "world-"+randomAlphabetic(5);

        stubProjectBulkRequest(projectId, projectSlug);

        final ArrayNode versionResp = objectMapper.createArrayNode();
        final ObjectNode versionNode = objectMapper.createObjectNode()
            .put("id", versionId)
            .put("project_id", projectId)
            .put("version_type", "release");
        versionNode.putArray("loaders").add("datapack");
        versionNode.putArray("files")
            .addObject()
            .put("url", wm.getRuntimeInfo().getHttpBaseUrl() + "/cdn/" + versionId + ".zip")
            .put("filename", versionId + ".zip");
        versionNode.putArray("dependencies");

        stubFor(get(urlPathEqualTo("/v2/version/" + versionId))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withJsonBody(versionNode)
            )
        );

        stubFor(get(urlPathMatching("/cdn/" + versionId + ".zip"))
            .willReturn(aResponse()
                .withBody("content of zip")
                .withHeader("Content-Type", "application/zip")
            )
        );

        final int exitCode = new CommandLine(
            new ModrinthCommand()
        )
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--world-directory",
                absoluteWorldDir ?
                    tempDir.resolve(worldDir).toString()
                    : worldDir,
                "--game-version", "1.21.1",
                "--loader", "datapack",
                "--projects", String.format("datapack:%s:%s", projectId, versionId)
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertThat(tempDir.resolve(worldDir).resolve("datapacks").resolve(versionId + ".zip"))
            .exists()
            .hasContent("content of zip");
    }

    @Test
    void handlesDatapacksLatestVersion(@TempDir Path tempDir) {
        final String projectId = randomAlphanumeric(6);
        final String projectSlug = randomAlphabetic(5);
        final String versionId = randomAlphanumeric(8 /*versionId's must have len 8*/);
        final String worldDir = "world-"+randomAlphabetic(5);

        stubProjectBulkRequest(projectId, projectSlug);

        final ArrayNode versionsResp = objectMapper.createArrayNode();
        final ObjectNode versionNode = versionsResp.addObject()
            .put("id", versionId)
            .put("project_id", projectId)
            .put("version_type", "release");
        versionNode.putArray("loaders").add("datapack");
        versionNode.putArray("files")
            .addObject()
            .put("url", wm.getRuntimeInfo().getHttpBaseUrl() + "/cdn/" + versionId + ".zip")
            .put("filename", versionId + ".zip");
        versionNode.putArray("dependencies");

        stubFor(get(urlPathEqualTo("/v2/project/" + projectId + "/version"))
            .withQueryParam("loaders", equalTo("[\"datapack\"]"))
            .withQueryParam("game_versions", equalTo("[\"1.21.1\"]"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withJsonBody(versionsResp)
            )
        );

        stubFor(get(urlPathMatching("/cdn/" + versionId + ".zip"))
            .willReturn(aResponse()
                .withBody("content of zip")
                .withHeader("Content-Type", "application/zip")
            )
        );

        final int exitCode = new CommandLine(
            new ModrinthCommand()
        )
            .execute(
                "--api-base-url", wm.getRuntimeInfo().getHttpBaseUrl(),
                "--output-directory", tempDir.toString(),
                "--world-directory", worldDir,
                "--game-version", "1.21.1",
                "--loader", "paper",
                "--projects", String.format("datapack:%s", projectId)
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertThat(tempDir.resolve(worldDir).resolve("datapacks").resolve(versionId + ".zip"))
            .exists()
            .hasContent("content of zip");
    }

    @NotNull
    private static RequestPatternBuilder projectVersionsRequest(String projectId) {
        return getRequestedFor(urlPathEqualTo("/v2/project/" + projectId + "/version"));
    }

    @NotNull
    private static AbstractPathAssert<?> assertVersionFile(Path tempDir, String versionId) {
        return assertThat(tempDir.resolve("plugins").resolve(versionId + ".jar"));
    }

    private void stubProjectBulkRequest(String projectId, String projectSlug) {
        final ArrayNode projectResp = objectMapper.createArrayNode();
        projectResp
            .addObject()
            .put("id", projectId)
            .put("slug", projectSlug)
            .put("project_type", "mod")
            .put("server_side", "required");
        stubFor(get(urlPathEqualTo("/v2/projects"))
            .withQueryParam("ids", equalTo("[\"" + projectId + "\"]"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withJsonBody(projectResp)
            )
        );
    }

    private void stubVersionRequest(String projectId, String versionId, Consumer<ArrayNode> depsAdder) {
        final ArrayNode versionResp = objectMapper.createArrayNode();
        final ObjectNode versionNode = versionResp
            .addObject()
            .put("id", versionId)
            .put("project_id", projectId)
            .put("version_type", "release");
        versionNode.putArray("files")
            .addObject()
            .put("url", wm.getRuntimeInfo().getHttpBaseUrl() + "/cdn/" + versionId)
            .put("filename", versionId + ".jar");
        final ArrayNode dependenciesArray = versionNode.putArray("dependencies");
        depsAdder.accept(dependenciesArray);

        stubFor(get(urlPathEqualTo("/v2/project/" + projectId + "/version"))
            .withQueryParam("loaders", equalTo("[\"paper\",\"spigot\"]"))
            .withQueryParam("game_versions", equalTo("[\"1.21.1\"]"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withJsonBody(versionResp)
            )
        );

    }

    private static void setupStubs() {
        stubFor(
            get("/v2/projects?ids=%5B%22fabric-api%22%2C%22cloth-config%22%5D")
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("modrinth-projects-fabric-api-cloth-config.json")
                )
        );
        stubFor(
            get("/v2/project/P7dR8mSH/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.19.2%22%5D")
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("modrinth-project-version-fabric-api.json")
                        .withTransformers("response-template")
                )
        );
        stubFor(
            get("/v2/project/9s6osm5g/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.19.2%22%5D")
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("modrinth-project-version-cloth-config.json")
                        .withTransformers("response-template")
                )
        );
        stubFor(
            get(urlPathMatching("/cdn/data/[^/]+/versions/[^/]+/(.+\\.jar)"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/java-archive")
                        .withBody("{{request.pathSegments.[5]}}")
                        .withTransformers("response-template")
                )
        );
    }
}