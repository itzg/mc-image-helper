package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class ModrinthCommandTest {
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