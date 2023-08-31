package me.itzg.helpers.properties;

import static com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemErr;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import me.itzg.helpers.env.MappedEnvVarProvider;
import org.apache.commons.lang3.RandomStringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
        propertiesFile = preparePropertiesFile();

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
        final int exitCode = new CommandLine(new SetPropertiesCommand()
        )
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

    private Path preparePropertiesFile() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("properties/server.properties")) {
            assertThat(in).isNotNull();
            final Path outFile = tempDir.resolve("server.properties");
            Files.copy(in, outFile);
            return outFile;
        }
    }
}