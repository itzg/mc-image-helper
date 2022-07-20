package me.itzg.helpers.find;

import picocli.CommandLine.ITypeConverter;

public class FindTypeConverter implements ITypeConverter<FindType> {

    @Override
    public FindType convert(String value) {
        final String normalized = value.toLowerCase();

        for (final FindType findType : FindType.values()) {
            if (findType.name().startsWith(normalized)) {
                return findType;
            }
        }

        throw new IllegalArgumentException("Unknown FindType: " + value);
    }
}
