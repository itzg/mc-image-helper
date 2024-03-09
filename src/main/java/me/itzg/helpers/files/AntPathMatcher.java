package me.itzg.helpers.files;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AntPathMatcher {

    private final Pattern regex;

    public AntPathMatcher(Collection<String> patterns) {
        this.regex = patterns != null && !patterns.isEmpty() ?
            convertToRegex(patterns)
            : null;
    }

    private static Pattern convertToRegex(Collection<String> patterns) {
        return Pattern.compile(
            patterns.stream()
                .map(s ->
                    // swap these out temporarily to avoid stepping on each other
                    s.replace("**", "_DSTAR_")
                        .replace("*", "_STAR_")
                        // escape special characters
                        .replaceAll("[.(\\[]", "\\\\$0")
                        .replace("?", ".")
                        // ...and then turn into regex equivalent
                        .replace("_DSTAR_", ".*?")
                        .replace("_STAR_", "[^/]*?")
                )
                // ...and join the whole thing into alternates to end up with one regex to match
                // all the requested patterns
                .collect(Collectors.joining("|"))
        );
    }

    public boolean matches(String input) {
        return regex != null
            && regex.matcher(input).matches();
    }
}
