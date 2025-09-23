package me.itzg.helpers.curseforge;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import me.itzg.helpers.cache.ApiCachingDisabled;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidApiKeyException;
import me.itzg.helpers.errors.RateLimitException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch.Options;

@WireMockTest
class CurseForgeApiClientTest {

    @Test
    void apiKeyHeaderIsTrimmed(WireMockRuntimeInfo wmInfo) {
        @Language("JSON") final String body = "{\"data\": []}";
        stubFor(get("/v1/categories?gameId=test&classesOnly=true")
            .willReturn(aResponse()
                .withBody(body)
                .withHeader("Content-Type", "application/json")
            )
        );

        final CategoryInfo result;
        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "key\n", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            result = client.loadCategoryInfo(Collections.singleton("mc-mods"))
                .block();
        }

        assertThat(result).isNotNull();

        verify(getRequestedFor(urlEqualTo("/v1/categories?gameId=test&classesOnly=true"))
            .withHeader("x-api-key", equalTo("key"))
        );
    }

    @Test
    void errorMapForbiddenInvalidApiKeyJson(WireMockRuntimeInfo wmInfo) {
        @Language("JSON") final String body = "{\"error\": \"Invalid API key\"}";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
                .withHeader("Content-Type", "application/json")
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "invalid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(InvalidApiKeyException.class)
                .hasMessageContaining("Access to http://localhost:")
                .hasMessageContaining("is forbidden due to invalid API key.")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }

    @Test
    void errorMapForbiddenRateLimitJson(WireMockRuntimeInfo wmInfo) {
        @Language("JSON") final String body = "{\"error\": \"Too much traffic\"}";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
                .withHeader("Content-Type", "application/json")
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "valid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Access to http://localhost:")
                .hasMessageContaining("has been rate-limited: Too much traffic")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }

    @Test
    void errorMapForbiddenGenericJson(WireMockRuntimeInfo wmInfo) {
        @Language("JSON") final String body = "{\"error\": \"Some other error\"}";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
                .withHeader("Content-Type", "application/json")
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "valid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(GenericException.class)
                .hasMessageContaining("CurseForge API forbidden response: Some other error")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }

    @Test
    void errorMapForbiddenFetchingObjectContentFallback(WireMockRuntimeInfo wmInfo) {
        final String body = "Fetching object content";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "valid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(GenericException.class)
                .hasMessageContaining("CurseForge API returned forbidden for file metadata. This may indicate a temporary issue or unavailable file content.")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }

    @Test
    void errorMapForbiddenTrafficStringFallback(WireMockRuntimeInfo wmInfo) {
        final String body = "There might be too much traffic";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "valid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("Access to http://localhost:")
                .hasMessageContaining("has been rate-limited.")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }

    @Test
    void errorMapForbiddenGenericFallback(WireMockRuntimeInfo wmInfo) {
        final String body = "Some unknown forbidden message";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "valid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(GenericException.class)
                .hasMessageContaining("Access to http://localhost:")
                .hasMessageContaining("forbidden with message: Some unknown forbidden message")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }

    @Test
    void errorMapForbiddenJsonParseFailureFallback(WireMockRuntimeInfo wmInfo) {
        final String body = "Invalid JSON body";
        stubFor(get("/v1/mods/123/files/456")
            .willReturn(aResponse()
                .withStatus(403)
                .withBody(body)
            )
        );

        try (CurseForgeApiClient client = new CurseForgeApiClient(wmInfo.getHttpBaseUrl(), "valid", Options.builder().build(),
            "test", new ApiCachingDisabled()
        )) {
            assertThatThrownBy(() -> client.getModFileInfo(123, 456).block())
                .isInstanceOf(GenericException.class)
                .hasMessageContaining("Access to http://localhost:")
                .hasMessageContaining("forbidden with message: Invalid JSON body")
                .hasCauseInstanceOf(FailedRequestException.class);
        }
    }
}