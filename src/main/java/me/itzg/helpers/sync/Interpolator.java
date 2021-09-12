package me.itzg.helpers.sync;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.CharsetDetector;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private Result<String> interpolate(String str) throws IOException {
        final Matcher matcher = VAR_PATTERN.matcher(str);
        final StringBuffer sb = new StringBuffer();

        int replacements = 0;

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

            log.trace("Processing varName={} with value={}", varName, value);
            ++replacements;
            matcher.appendReplacement(sb, value != null ? value : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(sb);

        return new Result<>(sb.toString(), replacements);
    }

    private String readValueFromFile(String filename) throws IOException {
        final String content = new String(Files.readAllBytes(Paths.get(filename)), StandardCharsets.UTF_8);
        return content.trim();
    }

    public Result<byte[]> interpolate(byte[] content) throws IOException {
        Charset charset = CharsetDetector.detect(content);
        log.debug("Detected charset={}", charset);
        final Result<String> result = interpolate(new String(content, charset));
        return new Result<>(result.getContent().getBytes(charset), result.getReplacementCount());
    }

    @RequiredArgsConstructor
    @Data
    public static class Result<T> {
        final T content;
        final int replacementCount;
    }
}
