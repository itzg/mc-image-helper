package me.itzg.helpers.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.regex.PatternSyntaxException;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;

class MultiMatcherTest {

    @Test
    void nullMatcherMatchesAnyInput() {
        final MultiMatcher matcher = new MultiMatcher(null);

        assertThat(matcher.matches("anything")).isTrue();
    }

    @Test
    void emptyMatcherMatchesAnyInput() {
        final MultiMatcher matcher = new MultiMatcher("");

        assertThat(matcher.matches("anything")).isTrue();
    }

    @Test
    void emptyDelimitedRegexMatchesAnyInput() {
        final MultiMatcher matcher = new MultiMatcher("//");

        assertThat(matcher.matches("anything")).isTrue();
    }

    @Test
    void nonDelimitedMatcherUsesLiteralSubstringMatching() {
        final MultiMatcher matcher = new MultiMatcher("1.5");

        assertThat(matcher.matches("pack-1.5.zip")).isTrue();
        assertThat(matcher.matches("pack-1x5.zip")).isFalse();
    }

    @Test
    void regexMatcherUsesFindAndSupportsEndAnchor() {
        final MultiMatcher matcher = new MultiMatcher("/7\\.3\\.zip$/");

        assertThat(matcher.matches("10-7.3.zip")).isTrue();
        assertThat(matcher.matches("10-7.3.1.zip")).isFalse();
    }

    @Test
    void regexMatcherSupportsStartAndEndAnchors() {
        final MultiMatcher matcher = new MultiMatcher("/^10-7\\.3\\.zip$/");

        assertThat(matcher.matches("10-7.3.zip")).isTrue();
        assertThat(matcher.matches("prefix-10-7.3.zip")).isFalse();
    }

    @Test
    void partiallyDelimitedMatcherUsesLiteralSubstringMatching() {
        final MultiMatcher matcher = new MultiMatcher("/7\\.3\\.zip");

        assertThat(matcher.matches("10-7.3.zip")).isFalse();
        assertThat(matcher.matches("pack-/7\\.3\\.zip")).isTrue();
    }

    @Test
    void malformedRegexThrowsInvalidParameterException() {
        assertThatThrownBy(() -> new MultiMatcher("/[/"))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessage("Invalid regex pattern")
            .hasCauseInstanceOf(PatternSyntaxException.class);
    }
}
