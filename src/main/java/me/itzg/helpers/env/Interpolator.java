package me.itzg.helpers.env;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.CharsetDetector;
import me.itzg.helpers.errors.InvalidParameterException;

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

    public Result<String> interpolate(String str) throws IOException {
        final Matcher matcher = VAR_PATTERN.matcher(str);
        final StringBuffer sb = new StringBuffer();

        int replacements = 0;
        final List<String> missingVariables = new ArrayList<>();

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
                if (value == null) {
                    missingVariables.add(varName);
                }
            }

            log.trace("Processing varName={} with value={}", varName, value);
            if (value != null) {
                ++replacements;
            }
            else {
                // just use the variable-looking text as-is
                value = matcher.group();
            }

            try {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } catch (Exception e) {
                throw new InterpolationException(
                    "Failed to replace value of the variable '"+varName + "' with: "+value,
                    e
                );
            }
        }
        matcher.appendTail(sb);

        return new Result<>(sb.toString(), replacements, missingVariables);
    }

    private String readValueFromFile(String filename) throws IOException {
        final byte[] content;
        try {
            content = Files.readAllBytes(Paths.get(filename));
        } catch (IOException e) {
            // A declared value source that cannot be read is a configuration error rather than
            // something to continue past: callers would otherwise carry on and produce a result
            // that is missing the very value this file was named to supply.
            throw new InvalidParameterException(
                String.format("Failed to read placeholder value from %s", filename), e, true
            );
        }
        return new String(content, StandardCharsets.UTF_8).trim();
    }

    public Result<byte[]> interpolate(byte[] content) throws IOException {
        CharsetDetector.Result charsetResult = CharsetDetector.detect(content);
        log.debug("Detected charset={}", charsetResult.getCharset());
        final Result<String> result = interpolate(charsetResult.getContent().toString());
        return new Result<>(
                result.getContent().getBytes(charsetResult.getCharset()),
                result.getReplacementCount(),
                result.getMissingVariables()
        );
    }

    @RequiredArgsConstructor
    @Data
    public static class Result<T> {
        final T content;
        final int replacementCount;
        final List<String> missingVariables;
    }
}
