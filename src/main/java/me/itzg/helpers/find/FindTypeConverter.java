package me.itzg.helpers.find;

import picocli.CommandLine.ITypeConverter;

/**
 * Allows command-line arg to be spelled out enum name or any prefer thereof such as "f" for file
 */
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
