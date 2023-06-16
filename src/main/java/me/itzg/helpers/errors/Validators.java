package me.itzg.helpers.errors;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

public class Validators {
    public static final Pattern VERSION_OR_LATEST = Pattern.compile("latest|\\d+(\\.\\d+)+(-.+)?", Pattern.CASE_INSENSITIVE);
    public static final String DESCRIPTION_MINECRAFT_VERSION = "May be 'latest' or specific version";

    public static String validateMinecraftVersion(CommandSpec spec, String input) {
        final Matcher m = VERSION_OR_LATEST.matcher(input);
        if (m.matches()) {
            return input.toLowerCase();
        }

        throw new ParameterException(spec.commandLine(), "Invalid value for minecraft version: " + input);
    }
}
