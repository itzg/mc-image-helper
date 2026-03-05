package me.itzg.helpers.get;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.net.MalformedURLException;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

@WireMockTest
class ExistsTest {

    @Test
    void okWhenExists(WireMockRuntimeInfo wmRuntimeInfo) throws MalformedURLException {
        stubFor(head(urlPathEqualTo("/exists"))
            .willReturn(ok())
        );

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "--exists",
                    wmRuntimeInfo.getHttpBaseUrl() + "/exists"
                );

        assertThat(status).isEqualTo(0);
    }

    @Test
    void notOkWhenOneMissing(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(head(urlPathEqualTo("/exists"))
            .willReturn(ok()));
        stubFor(head(urlPathEqualTo("/notHere"))
            .willReturn(notFound()));

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "--exists",
                    wm.getHttpBaseUrl() + "/exists",
                    wm.getHttpBaseUrl() + "/notHere"
                );

        assertThat(status).isEqualTo(ExitCode.SOFTWARE);
    }

    @Test
    void includesAcceptHeader(WireMockRuntimeInfo wm) throws MalformedURLException {
        stubFor(head(urlPathEqualTo("/exists"))
            .withHeader("accept", equalTo("text/plain"))
            .willReturn(ok("Here!"))
        );

        final int status =
            new CommandLine(new GetCommand())
                .execute(
                    "--exists",
                    "--accept", "text/plain",
                    wm.getHttpBaseUrl() + "/exists"
                );

        assertThat(status).isEqualTo(0);
    }
}
