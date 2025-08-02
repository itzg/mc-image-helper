package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@WireMockTest
class VersionFromModrinthProjectsCommandTest {

    @ParameterizedTest
    @FieldSource("processGameVersionsArgs")
    void processGameVersions(List<List<String>> versions, String expected) {
        final String result = VersionFromModrinthProjectsCommand.processGameVersions(versions);

        if (expected != null) {
            assertThat(result)
                .isNotNull()
                .isEqualTo(expected);
        }
        else {
            assertThat(result)
                .isNull();
        }
    }

    @SuppressWarnings("unused") // will be fixed https://youtrack.jetbrains.com/issue/IDEA-358214/Support-JUnit-5-FieldSource-annotation
    static List<Arguments> processGameVersionsArgs = Arrays.asList(
        argumentSet("matches", Arrays.asList(
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8")
            ), "1.21.8"
        ),
        argumentSet("justOneOff", Arrays.asList(
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.6", "1.21.7"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8")
            ), "1.21.7"
        ),
        argumentSet("mismatch", Arrays.asList(
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8"),
                Arrays.asList("1.21.4", "1.21.5"),
                Arrays.asList("1.21.6", "1.21.7", "1.21.8")
            ), null
        )
    );

    @Test
    void testCommand(WireMockRuntimeInfo wmInfo) throws Exception {

        stubGetProjects("viaversion", "viabackwards", "griefprevention", "discordsrv");

        final String out = SystemLambda.tapSystemOut(() -> {
            final int exitCode = new CommandLine(new VersionFromModrinthProjectsCommand())
                .execute(
                    "--api-base-url", wmInfo.getHttpBaseUrl(),
                    "--projects", "viaversion,viabackwards,griefprevention,discordsrv"
                );

            assertThat(exitCode)
                .isEqualTo(ExitCode.OK);
        });

        assertThat(out).isEqualToNormalizingNewlines("1.21.7\n");
    }

    @Test
    void testCommandWithProjectQualifiers(WireMockRuntimeInfo wmInfo) throws Exception {

        stubGetProjects("viaversion", "viabackwards", "griefprevention", "discordsrv");

        final String out = SystemLambda.tapSystemOut(() -> {
            final int exitCode = new CommandLine(new VersionFromModrinthProjectsCommand())
                .execute(
                    "--api-base-url", wmInfo.getHttpBaseUrl(),
                    "--projects", "paper:viaversion,viabackwards,griefprevention:ue7jAjJ5,discordsrv"
                );

            assertThat(exitCode)
                .isEqualTo(ExitCode.OK);
        });

        assertThat(out).isEqualToNormalizingNewlines("1.21.7\n");
    }

    private void stubGetProjects(String... projects) {
        for (final String project : projects) {
            stubFor(get(urlPathEqualTo("/v2/project/" + project))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("modrinth/project-" + project + ".json")
                )
            );
        }
    }
}