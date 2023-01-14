package me.itzg.helpers.http;

import java.net.URI;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;

public class PathOrUriConverter implements ITypeConverter<PathOrUri> {

    private static final Pattern URI_PATTERN = Pattern.compile("http(s)?://.*");

    @Override
    public PathOrUri convert(String value) throws Exception {
        if (URI_PATTERN.matcher(value).matches()) {
            return PathOrUri.uri(URI.create(value));
        }
        else {
            return PathOrUri.path(Paths.get(value));
        }
    }
}
