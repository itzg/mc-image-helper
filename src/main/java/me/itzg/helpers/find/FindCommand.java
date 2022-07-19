package me.itzg.helpers.find;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "find")
@Slf4j
public class FindCommand implements Callable<Integer> {

    @Option(names = "--type", defaultValue = "file", description = "Valid values: ${COMPLETION-CANDIDATES}")
    FindType type;

    @Option(names = "--name", split = ",", converter = PathMatcherConverter.class,
        description = "One or more glob patterns to match name part of the path")
    List<PathMatcher> names;

    @Option(names = "--exclude-name", split = ",", converter = PathMatcherConverter.class,
        description = "One or more glob patterns to exclude by looking at name part of the path. "
            + "If a pattern matches a directory's name, then its entire subtree is excluded.")
    List<PathMatcher> excludeNames;

    @Option(names = "--max-depth", description = "Unlimited depth if zero or negative")
    int maxDepth;

    @Option(names = "--just-shallowest")
    boolean justShallowest;

    @Option(names = "--stop-on-first")
    boolean stopOnFirst;

    @Option(names = "--fail-no-matches", defaultValue = "false")
    boolean failWhenMissing;

    @Option(names = "--output-count-only")
    boolean outputCountOnly;

    @Parameters(arity = "1..*")
    List<Path> startingPoints;

    @Override
    public Integer call() throws Exception {
        if (maxDepth <= 0) {
            maxDepth = Integer.MAX_VALUE;
        }

        if (justShallowest) {
            final TrackShallowest track = new TrackShallowest();
            walkStartingPoints(track);
            if (track.getShallowest() != null) {
                System.out.println(track.getShallowest().toString());
            }
            else if (failWhenMissing){
                return ExitCode.SOFTWARE;
            }
        }
        else {
            final int matchCount = walkStartingPoints(path -> {
                if (!outputCountOnly) {
                    System.out.println(path.toString());
                }
                return stopOnFirst ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
            });

            if (outputCountOnly) {
                System.out.println(matchCount);
            }
            else if (failWhenMissing && matchCount == 0) {
                return ExitCode.SOFTWARE;
            }
        }

        return ExitCode.OK;
    }

    private int walkStartingPoints(Function<Path,FileVisitResult> handleMatch) throws IOException {
        final FindFilesVisitor visitor = new FindFilesVisitor(type, names, excludeNames, handleMatch);
        for (final Path startingPoint : startingPoints) {
            Files.walkFileTree(
                startingPoint,
                EnumSet.noneOf(FileVisitOption.class),
                maxDepth,
                visitor
            );
        }
        return visitor.getMatchCount();
    }

    static class TrackShallowest implements Function<Path,FileVisitResult> {

        private Path shallowest;

        @Override
        public FileVisitResult apply(Path path) {
            if (shallowest == null || path.getNameCount() < shallowest.getNameCount()) {
                shallowest = path;
            }
            return FileVisitResult.SKIP_SIBLINGS;
        }

        public Path getShallowest() {
            return shallowest;
        }
    }
}
