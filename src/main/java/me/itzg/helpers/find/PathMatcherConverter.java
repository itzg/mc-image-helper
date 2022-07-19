package me.itzg.helpers.find;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import picocli.CommandLine.ITypeConverter;

public class PathMatcherConverter implements ITypeConverter<PathMatcher> {

    @Override
    public PathMatcher convert(String value) {
        return FileSystems.getDefault().getPathMatcher(
            "glob:" +
                // escape any Windows backslashes
                value.replace("\\", "\\\\")
        );
    }
}
