package me.itzg.helpers;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class Interpolator {

    private final static Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final String FILE_SUFFIX = "_FILE";

    private final EnvironmentVariablesProvider environmentVariablesProvider;
    private final String envPrefix;

    public Interpolator(EnvironmentVariablesProvider environmentVariablesProvider, String envPrefix) {
        this.environmentVariablesProvider = environmentVariablesProvider;
        this.envPrefix = envPrefix;
    }

    public void interpolate(BufferedReader reader, BufferedWriter writer) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            final Matcher matcher = VAR_PATTERN.matcher(line);
            final StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                final String varName = matcher.group(1);

                String value = null;
                if (varName.startsWith(envPrefix)) {
                    value = environmentVariablesProvider.get(varName+FILE_SUFFIX);
                    if (value != null) {
                        value = readValueFromFile(value);
                    }
                    else {
                        value = environmentVariablesProvider.get(varName);
                    }
                }

                log.debug("Processing varName={} with value={}", varName, value);
                matcher.appendReplacement(sb, value != null ? value : Matcher.quoteReplacement(matcher.group()));
            }
            matcher.appendTail(sb);

            writer.write(sb.toString());
            writer.write("\n");
        }
    }

    private String readValueFromFile(String filename) throws IOException {
        final String content = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
        return content.trim();
    }
}
