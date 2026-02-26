package me.itzg.helpers.modrinth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.github.stefanbirkner.systemlambda.SystemLambda;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
    static List<Arguments> processGameVersionsArgs = asList(
        argumentSet("matches", asList(
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.6", "1.21.7", "1.21.8")
            ), "1.21.8"
        ),
        argumentSet("justOneOff", asList(
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.6", "1.21.7"),
                asList("1.21.6", "1.21.7", "1.21.8")
            ), "1.21.7"
        ),
        argumentSet("mismatch", asList(
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.6", "1.21.7", "1.21.8"),
                asList("1.21.4", "1.21.5"),
                asList("1.21.6", "1.21.7", "1.21.8")
            ), null
        ),
        argumentSet("fabric-api + nucledoom", asList(
            // part of fabric-api
                asList("24w46a",
                    "1.21.4-pre1",
                    "1.21.4-pre2",
                    "1.21.4-pre3",
                    "1.21.4-rc3",
                    "1.21.4",
                    "25w02a",
                    "25w03a",
                    "25w04a",
                    "25w05a",
                    "25w06a",
                    "25w07a",
                    "25w08a",
                    "25w09a",
                    "25w09b",
                    "25w10a",
                    "1.21.5-pre1",
                    "1.21.5-pre2",
                    "1.21.5-pre3",
                    "1.21.5-rc1",
                    "1.21.5-rc2",
                    "1.21.5",
                    "25w14craftmine",
                    "25w15a",
                    "25w16a",
                    "25w17a",
                    "25w18a",
                    "25w19a",
                    "25w20a",
                    "25w21a",
                    "1.21.6-pre1",
                    "1.21.6-pre3",
                    "1.21.6",
                    "1.21.7-rc1",
                    "1.21.7",
                    "1.21.8",
                    "25w31a",
                    "25w32a"),
            // part of nucledoom
            singletonList("1.21.4")
            ), "1.21.4"
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

        assertThat(out).isEqualToNormalizingNewlines("1.21.10\n");
    }

    @Test
    void testCommandFabric(WireMockRuntimeInfo wmInfo) throws Exception {

        stubGetProjects("fabric-api", "nucledoom");

        final String out = SystemLambda.tapSystemOut(() -> {
            final int exitCode = new CommandLine(new VersionFromModrinthProjectsCommand())
                .execute(
                    "--api-base-url", wmInfo.getHttpBaseUrl(),
                    "--projects", "fabric-api, nucledoom"
                );

            assertThat(exitCode)
                .isEqualTo(ExitCode.OK);
        });

        assertThat(out).isEqualToNormalizingNewlines("1.21.4\n");
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

        assertThat(out).isEqualToNormalizingNewlines("1.21.10\n");
    }

    private void stubGetProjects(String... projects) {
        for (final String project : projects) {
            stubFor(get(urlPathEqualTo("/v2/project/" + project + "/version"))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("modrinth/project-" + project + "-versions.json")
                )
            );
        }
    }
}