package me.itzg.helpers.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class FailedRequestExceptionTest {

    @Test
    void obfuscate() {
        assertThat(FailedRequestException.obfuscate(
            URI.create("https://example.com/path?query=value")
        ))
            .isEqualTo("https://example.com/path?query=value");
        assertThat(FailedRequestException.obfuscate(
            URI.create("https://user:pass@example.com/path?query=value")
        ))
            .isEqualTo("https://*:*@example.com/path?query=value");
    }
}