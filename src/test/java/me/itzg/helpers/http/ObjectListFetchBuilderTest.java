package me.itzg.helpers.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static me.itzg.helpers.http.Fetch.fetch;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import lombok.Data;
import org.junit.jupiter.api.Test;

@WireMockTest
class ObjectListFetchBuilderTest {

    @Data
    static class Entry {
        private String name;
    }

    @Test
    void testBasicScenario(WireMockRuntimeInfo wm) throws IOException {
        stubFor(
            get("/content")
                .withHeader("accept", WireMock.equalTo("application/json"))
                .willReturn(
                    jsonResponse(
                        "[\n"
                            + "  {\n"
                            + "    \"name\": \"alpha\"\n"
                            + "  },\n"
                            + "  {\n"
                            + "    \"name\": \"beta\"\n"
                            + "  }\n"
                            + "]", 200)
                )
        );

        final List<Entry> results = fetch(URI.create(wm.getHttpBaseUrl() + "/content"))
            .toObjectList(Entry.class)
            .assemble()
            .block();

        assertThat(results)
            .hasSize(2)
            .extracting("name")
            .contains("alpha", "beta");
    }
}