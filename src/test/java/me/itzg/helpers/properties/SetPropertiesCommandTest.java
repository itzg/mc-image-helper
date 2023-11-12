package me.itzg.helpers.properties;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.stream.Stream;
import me.itzg.helpers.env.MappedEnvVarProvider;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import picocli.CommandLine.ExitCode;

class SetPropertiesCommandTest {

    @TempDir
    Path tempDir;

    private Path propertiesFile;
    private Path definitionsFile;
    private Properties originalProperties;

    @BeforeEach
    void setUp() throws IOException, URISyntaxException {
        propertiesFile = preparePropertiesFile("server.properties");

        final URL definitionsResource = getClass().getResource("/properties/property-definitions.json");
        assertThat(definitionsResource).isNotNull();
        definitionsFile = Paths.get(definitionsResource.toURI());

        originalProperties = loadProperties();
    }

    @Test
    void simpleNeedsChange() throws Exception {
        final String name = RandomStringUtils.randomAlphabetic(10);

        final int exitCode = new CommandLine(new SetPropertiesCommand()
            .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                "SERVER_NAME", name
            ))
        )
            .execute(
                "--definitions", definitionsFile.toString(),
                propertiesFile.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        final Properties properties = loadProperties();

        assertThat(properties).containsEntry("server-name", name);
        assertPropertiesEqualExcept(properties, "server-name");
    }

    @Test
    void hasMapping() throws Exception {

        final int exitCode = new CommandLine(new SetPropertiesCommand()
            .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                "GAMEMODE", "1"
            ))
        )
            .execute(
                "--definitions", definitionsFile.toString(),
                propertiesFile.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        final Properties properties = loadProperties();

        assertThat(properties).containsEntry("gamemode", "creative");
        assertPropertiesEqualExcept(properties, "gamemode");
    }

    @Test
    void disallowedValue() throws Exception {
        final String err = tapSystemErr(() -> {
            final int exitCode = new CommandLine(new SetPropertiesCommand()
                .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                    "ONLINE_MODE", "invalid"
                ))
            )
                .execute(
                    "--definitions", definitionsFile.toString(),
                    propertiesFile.toString()
                );

            assertThat(exitCode).isNotEqualTo(ExitCode.OK);
        });

        assertThat(err)
            .contains("ONLINE_MODE")
            .contains("InvalidParameterException");

    }

    @Test
    void removesMarkedForRemoval() throws IOException {
        final Path hasWhiteList = Files.write(tempDir.resolve("old.properties"), Collections.singletonList("white-list=true"));
        final int exitCode = new CommandLine(new SetPropertiesCommand())
            .execute(
                "--definitions", definitionsFile.toString(),
                hasWhiteList.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        final Properties properties = new Properties();
        try (InputStream propsIn = Files.newInputStream(hasWhiteList)) {
            properties.load(propsIn);
        }

        assertThat(properties).doesNotContainKey("white-list");
    }

    @Test
    void handlesNewCustomProperty() throws IOException {
        final Path outputProperties = tempDir.resolve("output.properties");

        final int exitCode = new CommandLine(new SetPropertiesCommand())
            .execute(
                "--custom-property", "key1=value1",
                outputProperties.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        final Properties properties = new Properties();
        try (InputStream propsIn = Files.newInputStream(outputProperties)) {
            properties.load(propsIn);
        }

        assertThat(properties)
            .containsEntry("key1", "value1");
    }

    @Test
    void handlesModifiedCustomProperties() throws IOException {
        final Path outputProperties = tempDir.resolve("output.properties");
        Files.write(outputProperties, Collections.singletonList("key1=value1"));

        final int exitCode = new CommandLine(new SetPropertiesCommand())
            .execute(
                "--custom-property", "key1=newValue1",
                "--custom-property", "key2=value2",
                outputProperties.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        final Properties properties = new Properties();
        try (InputStream propsIn = Files.newInputStream(outputProperties)) {
            properties.load(propsIn);
        }

        assertThat(properties)
            .hasSize(2)
            .containsEntry("key1", "newValue1")
            .containsEntry("key2", "value2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"with-unicode.txt", "with-escapes.txt"})
    void handlesExistingUnicodePropertyValue(String filename) throws IOException {
        final Path outputProperties = preparePropertiesFile(filename);

        final int exitCode = new CommandLine(new SetPropertiesCommand())
            .execute(
                "--custom-property", "key1=value1",
                outputProperties.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        assertThat(outputProperties)
            .content(StandardCharsets.UTF_8)
            .containsIgnoringNewLines("motd=§c§lT§6§le§e§ls§a§lt§3§li§9§ln§5§lg §6§l1§e§l2§a§l3")
            .containsIgnoringNewLines("key1=value1");
    }

    @Test
    void encodesWithGivenEncoding() {
        final Path outputProperties = tempDir.resolve("ascii.properties");

        final int exitCode = new CommandLine(new SetPropertiesCommand()
            .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                "MOTD", "§ctest"
            ))
        )
            .execute(
                "--definitions", definitionsFile.toString(),
                "--escape-unicode",
                outputProperties.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        //noinspection UnnecessaryUnicodeEscape
        assertThat(outputProperties)
            .content()
            .containsIgnoringNewLines("motd=\\u00A7ctest");

    }

    @Test
    void encodesPreEscaped() {
        final Path outputProperties = tempDir.resolve("out.properties");

        final int exitCode = new CommandLine(new SetPropertiesCommand()
            .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                "LEVEL_NAME", "sv\\u011Bt"
            ))
        )
            .execute(
                "--definitions", definitionsFile.toString(),
                outputProperties.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        //noinspection UnnecessaryUnicodeEscape
        assertThat(outputProperties)
            .content(StandardCharsets.UTF_8)
            .containsIgnoringNewLines("level-name=svět");

    }

    public static Stream<Arguments> processesPlaceholdersArgs() {
        return Stream.of(
            arguments("simple", "simple"),
            arguments("Running %MODPACK_NAME%", "Running modpack"),
            arguments("Running %env:MODPACK_NAME%", "Running modpack"),
            arguments("Running %MODPACK_NAME% at %MODPACK_VERSION%", "Running modpack at version"),
            arguments("Date is %date:yyyy-MM-dd%", "Date is 2007-12-03"),
            arguments("Stays %UNKNOWN%", "Stays %UNKNOWN%"),
            arguments("%%", "%%"),
            arguments("%MODPACK_NAME% and stray %", "modpack and stray %")
        );
    }

    @ParameterizedTest
    @MethodSource("processesPlaceholdersArgs")
    void processesPlaceholders(String motd, String expected) {
        final Path outputProperties = tempDir.resolve("out.properties");

        final int exitCode = new CommandLine(new SetPropertiesCommand()
            .setEnvironmentVariablesProvider(MappedEnvVarProvider.of(
                "MODPACK_NAME", "modpack",
                "MODPACK_VERSION", "version",
                "MOTD", motd
            ))
            .setClock(Clock.fixed(Instant.parse("2007-12-03T10:15:30.00Z"), ZoneId.of("UTC")))
        )
            .execute(
                "--definitions", definitionsFile.toString(),
                outputProperties.toString()
            );

        assertThat(exitCode).isEqualTo(ExitCode.OK);

        //noinspection UnnecessaryUnicodeEscape
        assertThat(outputProperties)
            .content(StandardCharsets.UTF_8)
            .containsIgnoringNewLines("motd=" + expected);

    }

    private void assertPropertiesEqualExcept(Properties properties, String... propertiesToIgnore) {
        final HashSet<Object> actualKeys = new HashSet<>(properties.keySet());
        Arrays.asList(propertiesToIgnore).forEach(actualKeys::remove);
        final HashSet<Object> originalKeys = new HashSet<>(originalProperties.keySet());
        Arrays.asList(propertiesToIgnore).forEach(originalKeys::remove);

        assertThat(actualKeys).isEqualTo(originalKeys);

        for (final Object key : originalKeys) {
            assertThat(properties.get(key)).withFailMessage(() -> String.format("Property %s does not equal", key))
                .isEqualTo(originalProperties.get(key));
        }
    }

    @NotNull
    private Properties loadProperties() throws IOException {
        final Properties properties = new Properties();
        try (InputStream propsIn = Files.newInputStream(propertiesFile)) {
            properties.load(propsIn);
        }
        return properties;
    }

    private Path preparePropertiesFile(String filename) throws IOException {
        final URL resource = getClass().getClassLoader().getResource("properties/" + filename);
        assertThat(resource).isNotNull();
        try {
            return Files.copy(Paths.get(resource.toURI()), tempDir.resolve(filename));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}