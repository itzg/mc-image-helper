package me.itzg.helpers.properties;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.env.EnvironmentVariablesProvider;
import me.itzg.helpers.env.StandardEnvironmentVariablesProvider;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.json.ObjectMappers;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "set-properties", description = "Maps environment variables to a properties file")
@Slf4j
public class SetPropertiesCommand implements Callable<Integer> {

    public static final TypeReference<Map<String, PropertyDefinition>> PROPERTY_DEFINITIONS_TYPE = new TypeReference<Map<String, PropertyDefinition>>() {
    };

    @Option(names = "--definitions", required = true, description = "JSON file of property names to PropertyDefinition mappings")
    Path propertyDefinitions;

    @Parameters(arity = "1")
    Path propertiesFile;

    @Setter
    private EnvironmentVariablesProvider environmentVariablesProvider = new StandardEnvironmentVariablesProvider();

    @Override
    public Integer call() throws Exception {

        if (!Files.exists(propertyDefinitions)) {
            throw new InvalidParameterException("Property definitions file does not exist");
        }

        final Map<String, PropertyDefinition> propertyDefinitions = ObjectMappers.defaultMapper()
            .readValue(this.propertyDefinitions.toFile(), PROPERTY_DEFINITIONS_TYPE);

        final Properties properties = new Properties();
        if (Files.exists(propertiesFile)) {
            try (InputStream propsIn = Files.newInputStream(propertiesFile)) {
                properties.load(propsIn);
            }
        }

        final long changes = processProperties(propertyDefinitions, properties);
        if (changes > 0) {
            log.info("Created/updated {} propert{} in {}", changes, changes != 1 ? "ies":"y", propertiesFile);

            try (OutputStream propsOut = Files.newOutputStream(propertiesFile, StandardOpenOption.TRUNCATE_EXISTING)) {
                properties.store(propsOut, String.format("Updated %s by mc-image-helper", Instant.now()));
            }
        }

        return ExitCode.OK;
    }

    /**
     * @return count of added/modified properties
     */
    private long processProperties(Map<String, PropertyDefinition> propertyDefinitions, Properties properties) {
        return propertyDefinitions.entrySet().stream()
            .map(entry -> {
                final String name = entry.getKey();
                final PropertyDefinition definition = entry.getValue();

                if (definition.isRemove()) {
                    if (properties.containsKey(name)) {
                        log.debug("Removing {}, which is marked for removal", name);
                        properties.remove(name);
                        return true;
                    }
                    else {
                        return false;
                    }
                }

                final String envValue = environmentVariablesProvider.get(definition.getEnv());
                if (envValue != null) {
                    final String expectedValue = mapAndValidateValue(definition, envValue);

                    final String propValue = properties.getProperty(name);

                    if (!Objects.equals(expectedValue, propValue)) {
                        log.debug("Setting property {} to new value '{}'", name, expectedValue);
                        properties.setProperty(name, expectedValue);
                        return true;
                    }
                }

                return false;
            })
            .filter(modified -> modified)
            .count();
    }

    private String mapAndValidateValue(PropertyDefinition definition, String value) {
        if (definition.getMappings() != null) {
            value = definition.getMappings().getOrDefault(value, value);
        }
        if (definition.getAllowed() != null) {
            if (!definition.getAllowed().contains(value)) {
                throw new InvalidParameterException(
                    String.format("The environment variable %s does not contain an allowed value '%s'. Allowed: %s",
                        definition.getEnv(), value, definition.getAllowed()
                    )
                );
            }
        }
        return value;
    }
}
