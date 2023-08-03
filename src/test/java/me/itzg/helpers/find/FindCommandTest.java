package me.itzg.helpers.find;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import me.itzg.helpers.MoreAssertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class FindCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void failWhenNoArgs() throws Exception {
        final String stderr = tapSystemErr(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute();

            assertThat(exitCode).isEqualTo(ExitCode.USAGE);
        });

        assertThat(stderr)
            .contains("Missing required parameter");
    }

    @Test
    void emptyWhenNoPatterns() throws Exception {
        Files.createFile(tempDir.resolve("file.txt"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(stdout).isBlank();
    }

    @Test
    void regularSuffixGlob() throws Exception {
        final Path one = Files.createFile(tempDir.resolve("one.txt"));
        final Path subDir = Files.createDirectories(tempDir.resolve("sub"));
        final Path two = Files.createFile(subDir.resolve("two.txt"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name", "*.txt",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            one.toString(),
            two.toString()
        );
    }

    @Test
    void findsDirectoryType() throws Exception {
        final Path thisDir = Files.createDirectories(tempDir.resolve("a").resolve("this"));
        Files.createFile(tempDir.resolve("this"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--type", "directory",
                    "--name", "this",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            thisDir.toString()
        );
    }

    @Test
    void findsFilesAndDirectories() throws Exception {
        final Path a1 = Files.createDirectories(tempDir.resolve("a1"));
        final Path a2 = Files.createFile(tempDir.resolve("a2"));
        Files.createFile(tempDir.resolve("b1"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--type=file",
                    "--type=directory",
                    "--name", "a*",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            a1.toString(),
            a2.toString()
        );
    }

    @Test
    void findsDirectoriesWithinDirectories(@TempDir Path tempDir) throws Exception {
        final Path this1 = Files.createDirectories(tempDir.resolve("this"));
        final Path this2 = Files.createDirectories(this1.resolve("this"));
        final Path this3 = Files.createDirectories(this2.resolve("this"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--type", "directory",
                    "--name", "this",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            this1.toString(),
            this2.toString(),
            this3.toString()
        );
    }

    @Test
    void findsShallowestFile() throws Exception {
        Files.createFile(
            Files.createDirectories(tempDir.resolve("a").resolve("b"))
                .resolve("this.txt")
        );
        final Path expected = Files.createFile(
            Files.createDirectories(tempDir.resolve("c"))
                .resolve("this.txt")
        );

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--only-shallowest",
                    "--name", "this.txt",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            expected.toString()
        );
    }

    @Test
    void findsShallowestDir() throws Exception {
        Files.createDirectories(tempDir.resolve("a").resolve("b").resolve("config"));
        final Path expected = Files.createDirectories(tempDir.resolve("c").resolve("config"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--only-shallowest",
                    "--type", "directory",
                    "--name", "config",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            expected.toString()
        );
    }

    @Test
    void appliesMaxDepth() throws Exception {
        final Path expected1 = Files.createFile(tempDir.resolve("one.txt"));
        final Path expected2 = Files.createFile(
            Files.createDirectories(tempDir.resolve("a"))
                .resolve("two.txt")
        );
        Files.createFile(
            Files.createDirectories(tempDir.resolve("b").resolve("c"))
                .resolve("three.txt")
        );

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*.txt",
                    "--max-depth=2",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout)
            .containsExactlyInAnyOrder(
                expected1.toString(),
                expected2.toString()
            );
    }

    @Nested
    class appliesMinDepth {

        @Test
        void dirAtStartingPoint() throws Exception {
            final Path a = Files.createDirectories(tempDir.resolve("a"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=*",
                        "--type=directory",
                        "--min-depth=1",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            MoreAssertions.assertThatLines(stdout)
                .containsExactlyInAnyOrder(
                    a.toString()
                );
        }

        @Test
        void defaultDepthZero() throws Exception {
            final Path a = Files.createDirectories(tempDir.resolve("a"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=*",
                        "--type=directory",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            MoreAssertions.assertThatLines(stdout)
                .containsExactlyInAnyOrder(
                    tempDir.toString(),
                    a.toString()
                );
        }

        @Test
        void fileInDir() throws Exception {
            final Path a = Files.createDirectories(tempDir.resolve("a"));
            final Path b = Files.createFile(a.resolve("b.txt"));
            Files.createFile(tempDir.resolve("c.txt"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=*.txt",
                        "--min-depth=2",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            MoreAssertions.assertThatLines(stdout)
                .containsExactlyInAnyOrder(
                    b.toString()
                );
        }
    }

    @Test
    void failsAsExpectedWhenNotFound() throws IOException {
        Files.createFile(tempDir.resolve("other.cfg"));

        final int exitCode = new CommandLine(new FindCommand())
            .execute(
                "--name=*.txt",
                "--fail-no-matches",
                tempDir.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.SOFTWARE);
    }

    @Test
    void outputsCountWhenRequested() throws Exception {
        Files.createFile(tempDir.resolve("one.txt"));
        Files.createFile(tempDir.resolve("two.txt"));
        Files.createFile(tempDir.resolve("three.txt"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*.txt",
                    "--output-count-only",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        assertThat(stdout).isEqualToNormalizingNewlines("3\n");
    }

    @Test
    void handlesMultipleNames() throws Exception {
        final Path one = Files.createFile(tempDir.resolve("one.txt"));
        final Path two = Files.createFile(tempDir.resolve("two.cfg"));
        final Path three = Files.createFile(tempDir.resolve("three.cfg"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*.txt,*.cfg",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            one.toString(),
            two.toString(),
            three.toString()
        );
    }

    @Test
    void stopsOnFirstMatch() throws Exception {
        final Path a = Files.createFile(tempDir.resolve("a"));
        final Path b = Files.createFile(tempDir.resolve("b"));
        final Path c = Files.createFile(tempDir.resolve("c"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=c,b,a",
                    "--stop-on-first",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsAnyOf(
            a.toString(),
            b.toString(),
            c.toString()
        );
    }

    @Test
    void handlesMultipleStartingPoints() throws Exception {
        final Path aDir = Files.createDirectories(tempDir.resolve("a"));
        final Path one = Files.createFile(aDir.resolve("one.txt"));
        final Path bDir = Files.createDirectories(tempDir.resolve("b"));
        final Path two = Files.createFile(bDir.resolve("two.txt"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*.txt",
                    aDir.toString(), bDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            one.toString(),
            two.toString()
        );
    }

    @Test
    void excludesByFiles() throws Exception {
        final Path a = Files.createFile(tempDir.resolve("a.txt"));
        Files.createFile(tempDir.resolve("b.dat"));
        final Path c = Files.createFile(tempDir.resolve("c.cfg"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*",
                    "--exclude-name=*.dat",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            a.toString(),
            c.toString()
        );
    }

    @Test
    void excludesByDirectory() throws Exception {
        Files.createFile(
            Files.createDirectories(tempDir.resolve("a").resolve("b"))
                .resolve("one.txt")
        );
        final Path two = Files.createFile(tempDir.resolve("a").resolve("two.txt"));
        final Path three = Files.createFile(tempDir.resolve("three.txt"));

        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*.txt",
                    "--exclude-name=b",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            two.toString(),
            three.toString()
        );
    }

    @Test
    void acceptsShortFindTypes() throws Exception {
        final Path expected = Files.createDirectories(tempDir.resolve("b"));
        final String stdout = tapSystemOut(() -> {
            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=b",
                    "--type", "d",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);
        });

        MoreAssertions.assertThatLines(stdout).containsExactlyInAnyOrder(
            expected.toString()
        );
    }

    @Nested
    class formatsDirname {

        @Test
        void atStartingPoint() throws Exception {
            Files.createFile(tempDir.resolve("one.txt"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=*.txt",
                        "--format", "%h",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            assertThat(stdout).hasLineCount(1);
            assertThat(stdout).isEqualToNormalizingNewlines(
                tempDir.toString() + "\n"
            );
        }

        @Test
        void oneLevelDeep() throws Exception {
            final Path expected = Files.createDirectories(tempDir.resolve("a"));
            Files.createFile(expected.resolve("one.txt"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=*.txt",
                        "--format", "%h",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            assertThat(stdout).hasLineCount(1);
            assertThat(stdout).isEqualToNormalizingNewlines(
                expected + "\n"
            );
        }

        @Test
        void shallowest() throws Exception {
            Files.createDirectories(tempDir.resolve("config"));
            Files.createDirectories(tempDir.resolve("a").resolve("config"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=config",
                        "--type=d",
                        "--only-shallowest",
                        "--format", "%h",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            assertThat(stdout).hasLineCount(1);
            assertThat(stdout).isEqualToNormalizingNewlines(
                tempDir.toString() + "\n"
            );
        }
    }

    @Nested
    class formatsRelative {

        @Test
        void topLevel() throws Exception {
            Files.createFile(tempDir.resolve("one.txt"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=*.txt",
                        "--format", "- %P",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            assertThat(stdout).hasLineCount(1);
            assertThat(stdout).isEqualToNormalizingNewlines(
                "- one.txt\n"
            );
        }

        @Test
        void shallowest() throws Exception {
            Files.createDirectories(tempDir.resolve("config"));
            Files.createDirectories(tempDir.resolve("a").resolve("config"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=config",
                        "--type=d",
                        "--only-shallowest",
                        "--format", "%P",
                        tempDir.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            assertThat(stdout).hasLineCount(1);
            assertThat(stdout).isEqualToNormalizingNewlines(
                "config\n"
            );
        }

        @Test
        void shallowestMultipleStartingPoints() throws Exception {
            final Path start1 = Files.createDirectories(tempDir.resolve("start1"));
            final Path start2 = Files.createDirectories(tempDir.resolve("deeper").resolve("start2"));

            Files.createDirectories(start1.resolve("a").resolve("config"));
            Files.createDirectories(start2.resolve("config"));

            final String stdout = tapSystemOut(() -> {
                final int exitCode = new CommandLine(new FindCommand())
                    .execute(
                        "--name=config",
                        "--type=d",
                        "--only-shallowest",
                        "--format", "%P",
                        start1.toString(),
                        start2.toString()
                    );

                assertThat(exitCode).isEqualTo(ExitCode.OK);
            });

            assertThat(stdout).hasLineCount(1);
            assertThat(stdout).isEqualToNormalizingNewlines(
                "config\n"
            );
        }
    }

    @Nested
    class deletes {

        @Test
        void nothingWhenNoMatches() throws IOException {
            final Path a = Files.createDirectories(tempDir.resolve("a"));
            final Path b = Files.createFile(a.resolve("b"));
            final Path c = Files.createFile(tempDir.resolve("c"));

            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--delete",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(a).exists();
            assertThat(b).exists();
            assertThat(c).exists();
        }

        @Test
        void files() throws IOException {
            final Path a = Files.createDirectories(tempDir.resolve("a"));
            final Path b = Files.createFile(a.resolve("b.txt"));
            final Path c = Files.createFile(tempDir.resolve("c.txt"));

            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=*.txt",
                    "--quiet",
                    "--delete",
                    "--min-depth", "1",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(a).doesNotExist();
            assertThat(b).doesNotExist();
            assertThat(c).doesNotExist();
            // but make sure the starting point was left alone
            assertThat(tempDir).exists();
        }

        @Test
        void directories() throws IOException {
            final Path a = Files.createDirectories(tempDir.resolve("dir-a"));
            final Path b = Files.createFile(a.resolve("b.txt"));
            final Path c = Files.createFile(tempDir.resolve("c.txt"));
            final Path d = Files.createDirectories(tempDir.resolve("dir-d"));
            final Path e = Files.createFile(d.resolve("e.txt"));

            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--name=dir-*",
                    "--exclude-name=dir-d",
                    "--type=directory",
                    "--quiet",
                    "--delete",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(a).doesNotExist();
            assertThat(b).doesNotExist();
            assertThat(c).exists();
            assertThat(d).exists();
            assertThat(e).exists();
        }

        @Test
        void withExcludesByFilesInSubdir() throws Exception {
            final Path a = Files.createFile(tempDir.resolve("a.txt"));
            final Path subdir = Files.createDirectories(tempDir.resolve("subdir"));
            final Path datFile = Files.createFile(subdir.resolve("b.dat"));
            final Path c = Files.createFile(tempDir.resolve("c.cfg"));
            final Path keepdir = Files.createDirectories(tempDir.resolve("keepdir"));
            final Path keepFile = Files.createFile(keepdir.resolve("keep.txt"));

            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--delete",
                    "--type", "f",
                    "--name=*",
                    "--exclude-name=*.dat,keepdir",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(datFile).exists();
            assertThat(keepFile).exists();
            assertThat(a).doesNotExist();
            assertThat(c).doesNotExist();
        }

        @Test
        void removesEmptiedDirectoriesByDefault() throws IOException {
            // directory with mix of file to keep and file to delete
            final Path keepDir = Files.createDirectories(tempDir.resolve("keepdir"));
            final Path keepDat = Files.createFile(keepDir.resolve("keep.dat"));
            Files.createFile(keepDir.resolve("delete.txt"));
            // directory with only a file to delete
            final Path emptyDir = Files.createDirectories(tempDir.resolve("empty"));
            Files.createFile(emptyDir.resolve("delete.txt"));
            // check recursive cleanup
            final Path bDir = Files.createDirectories(tempDir.resolve("b"));
            final Path dDir = Files.createDirectories(bDir.resolve("c/d"));
            Files.createFile(dDir.resolve("e.txt"));

            // but not an empty directory that wasn't touched
            final Path fDir = Files.createDirectories(tempDir.resolve("f"));

            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--delete",
                    "--type", "f",
                    "--name=*",
                    "--exclude-name=*.dat",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(keepDat).exists();
            assertThat(emptyDir).doesNotExist();
            assertThat(bDir).doesNotExist();
            assertThat(fDir).exists();
        }

        @Test
        void keepEmptyDirectoriesWhenRequested() throws IOException {
            final Path aDir = Files.createDirectories(tempDir.resolve("a"));
            final Path bFile = Files.createFile(aDir.resolve("delete.txt"));
            final Path dDir = Files.createDirectories(tempDir.resolve("b/c/d"));
            final Path eFile = Files.createFile(dDir.resolve("e.txt"));

            final int exitCode = new CommandLine(new FindCommand())
                .execute(
                    "--delete",
                    "--delete-empty-directories", "false",
                    "--type", "f",
                    "--name=*",
                    "--exclude-name=*.dat",
                    tempDir.toString()
                );

            assertThat(exitCode).isEqualTo(ExitCode.OK);

            assertThat(aDir).exists();
            assertThat(dDir).exists();
            assertThat(bFile).doesNotExist();
            assertThat(eFile).doesNotExist();
        }
    }

}