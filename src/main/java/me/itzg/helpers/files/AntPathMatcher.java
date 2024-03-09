package me.itzg.helpers.files;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class AntPathMatcher {

    private final List<PathMatcher> pathMatchers;

    public AntPathMatcher(Collection<String> patterns) {
        this.pathMatchers = patterns != null ?
            patterns.stream()
                .map(s -> FileSystems.getDefault().getPathMatcher("glob:" + s))
                .collect(Collectors.toList())
            : null;
    }

    public boolean matches(String input) {
        return pathMatchers != null &&
            (
                pathMatchers.isEmpty() ||
                    pathMatchers.stream()
                        .anyMatch(pathMatcher -> pathMatcher.matches(Paths.get(input)))
            );
    }
}
