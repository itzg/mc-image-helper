package me.itzg.helpers.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.http.Fetch.fetch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URI;
import lombok.Data;
import org.junit.jupiter.api.Test;

@WireMockTest
class ObjectFetchBuilderTest {

    @Data
    static class Content {
        private String name;
        private int count;
    }

    @Test
    void basicScenario(WireMockRuntimeInfo wm) throws IOException {
        stubFor(
            get("/content")
                .withHeader("accept", WireMock.equalTo("application/json"))
                .willReturn(
                    jsonResponse(
                        "{\n"
                            + "  \"name\": \"alpha\",\n"
                            + "  \"count\": 5\n"
                            + "}",
                        200)
                )
        );

        final Content result = fetch(URI.create(wm.getHttpBaseUrl() + "/content"))
            .toObject(Content.class)
            .assemble()
            .block();

        assertThat(result)
            .extracting("name", "count")
            .contains("alpha", 5);
    }

    @Test
    void responseHasContentTypeWithCharset(WireMockRuntimeInfo wm) throws IOException {
        stubFor(
            get("/content")
                .withHeader("accept", WireMock.equalTo("application/json"))
                .willReturn(
                    okForContentType("application/json; charset=utf-8", "{}")
                )
        );

        final Content result = fetch(URI.create(wm.getHttpBaseUrl() + "/content"))
            .toObject(Content.class)
            .assemble()
            .block();

        assertThat(result)
            .isNotNull();
    }

    @Test
    void handlesNotFound(WireMockRuntimeInfo wm) {
        stubFor(get(anyUrl())
            .willReturn(notFound())
        );

        assertThatThrownBy(() ->
            fetch(URI.create(wm.getHttpBaseUrl()))
            .toObject(String.class)
            .assemble()
            .block()
        )
            .isInstanceOf(FailedRequestException.class)
            .hasMessageContaining("404");
    }

    @Test
    void verifyAllExpectedHeaders(WireMockRuntimeInfo wm) throws IOException {
        stubFor(get(anyUrl())
            .willReturn(jsonResponse("{}", 200))
        );

        final Content result = fetch(URI.create(wm.getHttpBaseUrl() + "/"))
            .header("x-custom", "customValue")
            .toObject(Content.class)
            .execute();

        assertThat(result).isNotNull();

        verify(
            getRequestedFor(urlEqualTo("/"))
                .withHeader("accept", WireMock.equalTo("application/json"))
                .withHeader("x-custom", WireMock.equalTo("customValue"))
                .withHeader("user-agent", WireMock.containing("mc-image-helper"))
                .withHeader("x-fetch-session", WireMock.matching("[a-z0-9-]+"))
        );
    }
}