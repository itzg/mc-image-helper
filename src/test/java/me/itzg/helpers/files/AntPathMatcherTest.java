package me.itzg.helpers.files;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AntPathMatcherTest {

    @Test
    void literal() {
        final AntPathMatcher matcher = new AntPathMatcher(Arrays.asList("one", "two"));

        assertThat(matcher.matches("one"))
            .isTrue();
        assertThat(matcher.matches("two"))
            .isTrue();
        assertThat(matcher.matches("twooo"))
            .isFalse();
        assertThat(matcher.matches("three"))
            .isFalse();
    }

    @Test
    void fileSuffixTopDir() {
        final AntPathMatcher matcher = new AntPathMatcher(singletonList("*.jar"));

        assertThat(matcher.matches("mod.jar"))
            .isTrue();
    }

    @Test
    void butNotFileSuffixInSubdir() {
        final AntPathMatcher matcher = new AntPathMatcher(singletonList("*.jar"));

        assertThat(matcher.matches("mods/mod.jar"))
            .isFalse();
    }

    @Test
    void fileSuffixInSubdir() {
        final AntPathMatcher matcher = new AntPathMatcher(singletonList("**/*.jar"));

        assertThat(matcher.matches("mods/mod.jar"))
            .isTrue();
        assertThat(matcher.matches("extra/mods/mod.jar"))
            .isTrue();
    }
}