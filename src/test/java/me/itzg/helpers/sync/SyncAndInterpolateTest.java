package me.itzg.helpers.sync;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

class SyncAndInterpolateTest {

    @Nested
    class NonInterpolatedScenarios {
        @ParameterizedTest
        @ValueSource(classes = {Sync.class, SyncAndInterpolate.class})
        void copiesFromOneSrc(Class<?> commandClass, @TempDir Path tempDir) throws Exception {
            final Path srcDir = Files.createDirectory(tempDir.resolve("src"));
            Files.createFile(srcDir.resolve("test1.txt"));
            Files.createFile(srcDir.resolve("test2.txt"));
            final Path destDir = Files.createDirectory(tempDir.resolve("dest"));

            final String stderr = tapSystemErr(() -> {
                final int exitCode = new CommandLine(commandClass)
                .execute(
                    "--replace-env-file-suffixes=json",
                    srcDir.toString(),
                    destDir.toString()
                );

                assertThat(exitCode).isEqualTo(0);
            });
            assertThat(stderr).isBlank();

            assertThat(destDir).isNotEmptyDirectory();
            assertThat(destDir.resolve("test1.txt")).exists();
            assertThat(destDir.resolve("test2.txt")).exists();
        }

        @ParameterizedTest
        @ValueSource(classes = {Sync.class, SyncAndInterpolate.class})
        void copiesFromTwoSrc(Class<?> commandClass, @TempDir Path tempDir) throws Exception {
            final Path srcDir1 = Files.createDirectory(tempDir.resolve("src1"));
            Files.createFile(srcDir1.resolve("test1.txt"));
            Files.createFile(srcDir1.resolve("test2.txt"));
            final Path srcDir2 = Files.createDirectory(tempDir.resolve("src2"));
            Files.createFile(srcDir2.resolve("test3.txt"));
            Files.createFile(srcDir2.resolve("test4.txt"));
            final Path destDir = Files.createDirectory(tempDir.resolve("dest"));

            final String stderr = tapSystemErr(() -> {
                final int exitCode = new CommandLine(commandClass)
                    .execute(
                        "--replace-env-file-suffixes=json",
                        srcDir1.toString(),
                        srcDir2.toString(),
                        destDir.toString()
                    );

                assertThat(exitCode).isEqualTo(0);
            });
            assertThat(stderr).isBlank();

            assertThat(destDir).isNotEmptyDirectory();
            assertThat(destDir.resolve("test1.txt")).exists();
            assertThat(destDir.resolve("test2.txt")).exists();
            assertThat(destDir.resolve("test3.txt")).exists();
            assertThat(destDir.resolve("test4.txt")).exists();
        }

        @ParameterizedTest
        @ValueSource(classes = {Sync.class, SyncAndInterpolate.class})
        void copiesFromTwoSrcCommaDelim(Class<?> commandClass, @TempDir Path tempDir) throws Exception {
            final Path srcDir1 = Files.createDirectory(tempDir.resolve("src1"));
            Files.createFile(srcDir1.resolve("test1.txt"));
            Files.createFile(srcDir1.resolve("test2.txt"));
            final Path srcDir2 = Files.createDirectory(tempDir.resolve("src2"));
            Files.createFile(srcDir2.resolve("test3.txt"));
            Files.createFile(srcDir2.resolve("test4.txt"));
            final Path destDir = Files.createDirectory(tempDir.resolve("dest"));

            final String stderr = tapSystemErr(() -> {
                final int exitCode = new CommandLine(commandClass)
                    .execute(
                        "--replace-env-file-suffixes=json",
                        String.join(",", srcDir1.toString(), srcDir2.toString()),
                        destDir.toString()
                    );

                assertThat(exitCode).isEqualTo(0);
            });
            assertThat(stderr).isBlank();

            assertThat(destDir).isNotEmptyDirectory();
            assertThat(destDir.resolve("test1.txt")).exists();
            assertThat(destDir.resolve("test2.txt")).exists();
            assertThat(destDir.resolve("test3.txt")).exists();
            assertThat(destDir.resolve("test4.txt")).exists();
        }

        @ParameterizedTest
        @ValueSource(classes = {Sync.class, SyncAndInterpolate.class})
        void skipsMissingSrcDir(Class<?> commandClass, @TempDir Path tempDir) throws Exception {
            final Path srcDir1 = Files.createDirectory(tempDir.resolve("src"));
            Files.createFile(srcDir1.resolve("test1.txt"));
            Files.createFile(srcDir1.resolve("test2.txt"));
            final Path destDir = Files.createDirectory(tempDir.resolve("dest"));

            final String stderr = tapSystemErr(() -> {
                final int exitCode = new CommandLine(commandClass)
                    .execute(
                        "--replace-env-file-suffixes=json",
                        String.join(",", srcDir1.toString(), tempDir.resolve("missing").toString()),
                        destDir.toString()
                    );

                assertThat(exitCode).isEqualTo(0);
            });
            assertThat(stderr).isBlank();

            assertThat(destDir).isNotEmptyDirectory();
            assertThat(destDir.resolve("test1.txt")).exists();
            assertThat(destDir.resolve("test2.txt")).exists();
        }

    }

}