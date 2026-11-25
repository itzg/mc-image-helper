package me.itzg.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McImageHelperUserAgentTest {

    @Test
    void includesVersionUrlAndCommand() {
        assertThat(McImageHelper.userAgent("get", "1.2.3-SNAPSHOT", "https://example.org/project"))
            .isEqualTo("mc-image-helper/1.2.3-SNAPSHOT (https://example.org/project; cmd=get)");
    }

    @Test
    void replacesInvalidVersionAndDefaultsCommand() {
        assertThat(McImageHelper.userAgent(null, "???", "https://example.org/project"))
            .isEqualTo("mc-image-helper/unknown (https://example.org/project; cmd=unspecified)");
        assertThat(McImageHelper.userAgent("get", null, "https://example.org/project"))
            .isEqualTo("mc-image-helper/unknown (https://example.org/project; cmd=get)");
    }
}
