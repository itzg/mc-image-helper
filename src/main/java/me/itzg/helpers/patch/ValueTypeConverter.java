package me.itzg.helpers.patch;

import static me.itzg.helpers.McImageHelper.OPTION_SPLIT_COMMAS;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ValueTypeConverter {
    public static final String LIST_PREFIX = "list of ";

    public static final Map<String, Function<String, Object>> parsers = new HashMap<>();

    public static final String TYPE_INT = "int";
    public static final String TYPE_FLOAT = "float";
    public static final String TYPE_BOOL = "bool";
    public static final String TYPE_AUTO = "auto";
    public static final String TYPE_STRING = "string";

    static {
        parsers.put(TYPE_INT, Long::parseLong);
        parsers.put(TYPE_FLOAT, Double::parseDouble);
        parsers.put(TYPE_BOOL, ValueTypeConverter::parseBooleanStrict);
        parsers.put(TYPE_STRING, ValueTypeConverter::asString);
        parsers.put(TYPE_AUTO, ValueTypeConverter::autoNarrow);
    }

    private final boolean isListType;

    private final Function<String, Object> converter;

    public ValueTypeConverter(String type) {
        if (type == null) {
            converter = ValueTypeConverter::asString;
            isListType = false;
            return;
        }

        if (type.startsWith(LIST_PREFIX)) {
            isListType = true;
            type = type.substring(LIST_PREFIX.length());
        }
        else {
            isListType = false;
        }

        converter = parsers.getOrDefault(type, ValueTypeConverter::asString);
    }

    public Object convert(String strValue) {
        if (isListType) {
            final String[] parts = strValue.split(OPTION_SPLIT_COMMAS);
            return Arrays.stream(parts)
                    .map(converter)
                    .collect(Collectors.toList());
        }
        else {
            return converter.apply(strValue);
        }
    }

    private static Object parseBooleanStrict(String strValue) {
        switch (strValue.toLowerCase()) {
            case "true":
            case "yes":
                return true;
            case "false":
            case "no":
                return false;
        }

        throw new IllegalArgumentException("Not a valid boolean");
    }

    private static Object asString(String strValue) {
        return strValue;
    }

    private static Object autoNarrow(String strValue) {
        try {
            return Long.parseLong(strValue);
        } catch (NumberFormatException ignored) {}
        try {
            return Double.parseDouble(strValue);
        } catch (NumberFormatException ignored) {}
        try {
            return parseBooleanStrict(strValue);
        } catch (Exception ignored) {}

        return strValue;
    }
}
