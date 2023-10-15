package me.itzg.helpers.properties;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
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

    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);

    private static final TypeReference<Map<String, PropertyDefinition>> PROPERTY_DEFINITIONS_TYPE = new TypeReference<Map<String, PropertyDefinition>>() {
    };

    @Option(names = "--definitions", description = "JSON file of property names to PropertyDefinition mappings")
    Path propertyDefinitionsFile;

    @Option(names = {"--custom-property", "--custom-properties", "-p"},
        split = "\n", splitSynopsisLabel = "<NL>",
        description = "Key=value pairs of custom properties to set")
    Map<String,String> customProperties;

    @Parameters(arity = "1")
    Path propertiesFile;

    @Setter
    private EnvironmentVariablesProvider environmentVariablesProvider = new StandardEnvironmentVariablesProvider();

    @Override
    public Integer call() throws Exception {
        if (propertyDefinitionsFile == null && customProperties == null) {
            System.err.println("Either definitions or custom properties need to be provided");
            return ExitCode.USAGE;
        }

        final Map<String, PropertyDefinition> propertyDefinitions;
        if (propertyDefinitionsFile != null) {
            if (!Files.exists(propertyDefinitionsFile)) {
                throw new InvalidParameterException("Property definitions file does not exist");
            }

            propertyDefinitions = ObjectMappers.defaultMapper()
                .readValue(this.propertyDefinitionsFile.toFile(), PROPERTY_DEFINITIONS_TYPE);
        }
        else {
            propertyDefinitions = Collections.emptyMap();
        }

        final Properties properties = new Properties();
        if (Files.exists(propertiesFile)) {
            try (InputStream propsIn = Files.newInputStream(propertiesFile)) {
                properties.load(propsIn);
            }
        }

        final long changes = processProperties(propertyDefinitions, properties, customProperties);
        if (changes > 0) {
            log.info("Created/updated {} propert{} in {}", changes, changes != 1 ? "ies":"y", propertiesFile);

            try (OutputStream propsOut = Files.newOutputStream(propertiesFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                properties.store(propsOut, String.format("Updated %s by mc-image-helper", Instant.now()));
            }
        }

        return ExitCode.OK;
    }

    /**
     * @return count of added/modified properties
     */
    private long processProperties(Map<String, PropertyDefinition> propertyDefinitions, Properties properties,
        Map<String, String> customProperties
    ) {
        long modifiedViaDefinitions = 0;
        for (final Entry<String, PropertyDefinition> entry : propertyDefinitions.entrySet()) {
            final String name = entry.getKey();
            final PropertyDefinition definition = entry.getValue();

            if (definition.isRemove()) {
                if (properties.containsKey(name)) {
                    log.debug("Removing {}, which is marked for removal", name);
                    properties.remove(name);
                    ++modifiedViaDefinitions;
                }
            }
            else {
                final String envValue = environmentVariablesProvider.get(definition.getEnv());
                if (envValue != null) {
                    final String expectedValue = mapAndValidateValue(definition, envValue);

                    final String propValue = properties.getProperty(name);

                    if (!Objects.equals(expectedValue, propValue)) {
                        log.debug("Setting property {} to new value '{}'", name, needsValueRedacted(name) ? "***" : expectedValue);
                        properties.setProperty(name, expectedValue);
                        ++modifiedViaDefinitions;
                    }
                }
            }
        }

        long modifiedViaCustom = 0;
        if (customProperties != null) {
            for (final Entry<String, String> entry : customProperties.entrySet()) {
                final String name = entry.getKey();
                final String targetValue = entry.getValue();
                final String propValue = properties.getProperty(name);
                if (!Objects.equals(targetValue, propValue)) {
                    log.debug("Setting property {} to new value '{}'", name, targetValue);
                    properties.setProperty(name, targetValue);
                    ++modifiedViaCustom;
                }
            }
        }

        return modifiedViaDefinitions + modifiedViaCustom;
    }

    private static boolean needsValueRedacted(String name) {
        return name.contains("password");
    }

    /**
     * This will
     * <ul>
     *     <li>Apply mappings</li>
     *     <li>Normalize true|false values to lowercase</li>
     *     <li>Enforce allowed values</li>
     * </ul>
     * @return normalized value
     */
    private String mapAndValidateValue(PropertyDefinition definition, String value) {
        if (definition.getMappings() != null) {
            value = definition.getMappings().getOrDefault(value, value);
        }
        else {
            // normalize booleans to lowercase
            if (BOOLEAN_PATTERN.matcher(value).matches()) {
                return value.toLowerCase();
            }
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
