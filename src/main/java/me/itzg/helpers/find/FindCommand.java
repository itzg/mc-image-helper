package me.itzg.helpers.find;

import static me.itzg.helpers.McImageHelper.OPTION_SPLIT_COMMAS;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "find", description = "Specialized replacement for GNU's find")
@Slf4j
public class FindCommand implements Callable<Integer> {
    @SuppressWarnings("unused")
    @Option(names = {"-h", "--help"}, usageHelp = true)
    boolean help;

    @Option(names = {"--type", "-t"}, defaultValue = "file", split = OPTION_SPLIT_COMMAS,
        description = "Valid values: ${COMPLETION-CANDIDATES}",
        converter = FindTypeConverter.class
    )
    EnumSet<FindType> type;

    @Option(names = "--name", split = OPTION_SPLIT_COMMAS, paramLabel = "glob", converter = PathMatcherConverter.class,
        description = "One or more glob patterns to match name part of the path")
    List<PathMatcher> names;

    @Option(names = "--exclude-name", split = OPTION_SPLIT_COMMAS, paramLabel = "glob", converter = PathMatcherConverter.class,
        description = "One or more glob patterns to exclude by looking at name part of the path. "
            + "If a pattern matches a directory's name, then its entire subtree is excluded.")
    List<PathMatcher> excludeNames;

    @Option(names = "--min-depth", paramLabel = "N", defaultValue = "0",
        description = "Minimum match depth where 0 is a starting point")
    int minDepth;

    @Option(names = "--max-depth", paramLabel = "N", description = "Unlimited depth if negative")
    Integer maxDepth;

    @Option(names = "--only-shallowest")
    boolean justShallowest;

    @Option(names = "--stop-on-first")
    boolean stopOnFirst;

    @Option(names = "--fail-no-matches", defaultValue = "false")
    boolean failWhenMissing;

    @Option(names = "--output-count-only")
    boolean outputCountOnly;

    @Option(names = {"--quiet", "-q"})
    boolean quiet;

    @Option(names = "--format", description = "Applies a format when printing each matched entry. Supports the following directives%n"
        + "%%%% a literal %%%n"
        + "%%h leading directory of the entry%n"
        + "%%P path of the entry relative to the starting point")
    String format;

    @Option(names = "--delete", description = "Deletes the matched entries."
        + " When searching for directories, each directory and its contents will be recursively deleted.")
    boolean delete;

    @Option(names = "--delete-empty-directories",
        description = "Deletes a traversed directory if it becomes empty after matching files/directories within it were deleted",
        defaultValue = "true")
    boolean deleteEmptyDirectories;

    @Parameters(arity = "1..*", paramLabel = "startDir",
        description = "One or more starting directories")
    List<Path> startingPoints;

    @Override
    public Integer call() throws Exception {
        if (justShallowest) {
            final TrackShallowest track = new TrackShallowest();
            walkStartingPoints(track);
            if (track.matched()) {
                handleEntry(track.getShallowestStartingPoint(), track.getShallowest());
            }
            else if (failWhenMissing){
                return ExitCode.SOFTWARE;
            }
        }
        else {
            final int matchCount = walkStartingPoints(new MatchHandler() {
                @Override
                public FileVisitResult handle(Path startingPath, Path path) throws IOException {
                    if (!outputCountOnly) {
                        handleEntry(startingPath, path);
                    }
                    return stopOnFirst ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }

                @Override
                public void postDirectory(Path directory, int matchCount, int depth) throws IOException {
                    if (delete
                        && deleteEmptyDirectories
                        && matchCount > 0
                        && depth >= minDepth
                        && isDirectoryEmpty(directory)) {

                        log.debug("Deleting directory that had deleted files and became empty");
                        delete(directory);
                    }
                }
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

    private boolean isDirectoryEmpty(Path directory) throws IOException {
        try (DirectoryStream<Path> dir = Files.newDirectoryStream(directory)) {
            return !dir.iterator().hasNext();
        }
    }

    protected void handleEntry(Path startingPoint, Path path) throws IOException {
        final Path relPath = startingPoint.relativize(path);
        // FYI the empty relative path still has a name count of 1
        final int depth = startingPoint.equals(path) ? 0 : relPath.getNameCount();

        if (depth < minDepth) {
            log.debug("Skipping {} since it is not deeper than min depth", path);
            return;
        }

        if (delete) {
            delete(path);
        }

        if (!quiet) {
            if (format != null) {
                final Pattern directive = Pattern.compile("%(.)");

                final Matcher m = directive.matcher(format);
                final StringBuffer sb = new StringBuffer();

                while (m.find()) {
                    switch (m.group(1)) {
                        case "%":
                            m.appendReplacement(sb, "%");
                            break;
                        case "h":
                            m.appendReplacement(sb, Matcher.quoteReplacement(path.getParent().toString()));
                            break;
                        case "P":
                            m.appendReplacement(sb, Matcher.quoteReplacement(relPath.toString()));
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported format directive: " + m.group());
                    }
                }
                m.appendTail(sb);
                System.out.println(sb);
            }
            else {
                System.out.println(path);
            }
        }
    }

    private void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    log.debug("Deleting file={}", file);
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    log.debug("Deleting directory={}", dir);
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        else {
            Files.delete(path);
        }
    }

    private int walkStartingPoints(MatchHandler matchHandler) throws IOException {
        int total = 0;
        for (final Path startingPoint : startingPoints) {
            final FindFilesVisitor visitor = new FindFilesVisitor(type, names, excludeNames, startingPoint, matchHandler);
            Files.walkFileTree(
                startingPoint,
                EnumSet.noneOf(FileVisitOption.class),
                maxDepth == null || maxDepth < 0 ? Integer.MAX_VALUE : maxDepth,
                visitor
            );
            total += visitor.getMatchCount();
        }
        return total;
    }

}
