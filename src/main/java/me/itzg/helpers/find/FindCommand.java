package me.itzg.helpers.find;

import java.io.IOException;
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

    @Option(names = {"--type", "-t"}, defaultValue = "file", description = "Valid values: ${COMPLETION-CANDIDATES}",
        converter = FindTypeConverter.class
    )
    FindType type;

    @Option(names = "--name", split = ",", paramLabel = "glob", converter = PathMatcherConverter.class,
        description = "One or more glob patterns to match name part of the path")
    List<PathMatcher> names;

    @Option(names = "--exclude-name", split = ",", paramLabel = "glob", converter = PathMatcherConverter.class,
        description = "One or more glob patterns to exclude by looking at name part of the path. "
            + "If a pattern matches a directory's name, then its entire subtree is excluded.")
    List<PathMatcher> excludeNames;

    @Option(names = "--max-depth", paramLabel = "N", description = "Unlimited depth if zero or negative")
    int maxDepth;

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

    @Parameters(arity = "1..*", paramLabel = "startDir",
        description = "One or more starting directories")
    List<Path> startingPoints;

    @Override
    public Integer call() throws Exception {
        if (maxDepth <= 0) {
            maxDepth = Integer.MAX_VALUE;
        }

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
            final int matchCount = walkStartingPoints((startingPoint, path) -> {
                if (!outputCountOnly) {
                    handleEntry(startingPoint, path);
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

    protected void handleEntry(Path startingPoint, Path path) throws IOException {
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
                            m.appendReplacement(sb, Matcher.quoteReplacement(startingPoint.relativize(path).toString()));
                            break;
                        default:
                            throw new IllegalArgumentException("Unsupported format directive: " + m.group());
                    }
                }
                m.appendTail(sb);
                System.out.println(sb);
            }
            else {
                System.out.println(path.toString());
            }
        }
    }

    private void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
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
                maxDepth,
                visitor
            );
            total += visitor.getMatchCount();
        }
        return total;
    }

}
