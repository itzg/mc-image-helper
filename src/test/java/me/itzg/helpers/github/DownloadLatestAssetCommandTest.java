package me.itzg.helpers.github;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import java.nio.file.Path;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class DownloadLatestAssetCommandTest {
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .extensions(new ResponseTemplateTransformer(false))
        )
        .configureStaticDsl(true)
        .build();

    @Test
    void usingNamePattern(@TempDir Path tempDir, WireMockRuntimeInfo wmInfo) {
        final String filename = RandomStringUtils.randomAlphabetic(10)+".jar";
        final String fileContent = RandomStringUtils.randomAlphabetic(20);

        stubFor(get("/repos/org/repo/releases/latest")
            .willReturn(ok()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("github/release-with-sources-jar.json")
                .withTransformers("response-template")
                .withTransformerParameter("filename", filename)
            )
        );
        stubFor(head(urlPathEqualTo("/download/"+filename))
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
            )
        );
        stubFor(get("/download/"+filename)
            .willReturn(
                ok()
                    .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename))
                    .withBody(fileContent)
            )
        );

        final int exitCode = new CommandLine(new DownloadLatestAssetCommand())
            .execute(
                "--api-base-url", wmInfo.getHttpBaseUrl(),
                "--name-pattern", "app-.+?(?<!-sources)\\.jar",
                "--output-directory", tempDir.toString(),
                "org/repo"
            );

        assertThat(exitCode).isEqualTo(0);

        assertThat(tempDir.resolve(filename))
            .exists()
            .content().isEqualTo(fileContent);
    }
}