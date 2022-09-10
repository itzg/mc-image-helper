package me.itzg.helpers.assertcmd;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;

class EvalExistence {

    private static final Pattern globSymbols = Pattern.compile("[*?]|\\{.+?}");
    // matches everything up to the last path separator either / or \
    private static final Pattern pathSeparators = Pattern.compile(".*[/\\\\]");

    static boolean exists(String pathSpec) throws IOException {
        return !matchingPaths(pathSpec).paths.isEmpty();
    }

    @AllArgsConstructor
    static class MatchingPaths {

        final boolean globbing;
        final List<Path> paths;
    }

    static MatchingPaths matchingPaths(String pathSpec) throws IOException {
        final Matcher globMatcher = globSymbols.matcher(pathSpec);
        // find the first globbing symbol
        final boolean hasGlob = globMatcher.find();
        if (!hasGlob) {
            // no globbing, just a specific path
            final Path path = Paths.get(pathSpec);
            return new MatchingPaths(false,
                Files.exists(path) ? Collections.singletonList(path) : Collections.emptyList()
            );
        }

        // find last path separator in the text before the glob
        final Matcher sepMatcher = pathSeparators.matcher(pathSpec.substring(0, globMatcher.start()));
        final Path walkStart;
        // ...by looking from the start of the string
        if (sepMatcher.lookingAt()) {
            // ...and grabbing the end of the matched text
            walkStart = Paths.get(pathSpec.substring(0, sepMatcher.end()));
        }
        else {
            // no separator, so process relative paths
            walkStart = Paths.get("");
        }

        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(
            "glob:" +
                // escape any Windows backslashes
                pathSpec.replace("\\", "\\\\")
        );
        try (Stream<Path> pathStream = Files.walk(walkStart)) {
            return new MatchingPaths(true,
                pathStream
                    .filter(Files::isRegularFile)
                    .filter(pathMatcher::matches)
                    .collect(Collectors.toList())
            );
        }

    }

}
