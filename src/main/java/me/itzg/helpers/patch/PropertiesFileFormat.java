package me.itzg.helpers.patch;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;

/**
 * Properties files are a flat namespace of string values, so the decoded model is a flat map
 * of the keys exactly as they appear in the file. All escaping is delegated to
 * {@link Properties}; the only thing added here is preserving the key order of the original
 * file, which {@link Properties#store} would otherwise lose.
 * <p>
 * Comments are not retained, which matches the {@code set-properties} command.
 */
public class PropertiesFileFormat implements FileFormat {

    private static final String[] SUFFIXES = {"properties"};

    @Override
    public String[] getFileSuffixes() {
        return SUFFIXES;
    }

    @Override
    public String getName() {
        return "properties";
    }

    @Override
    public Map<String, Object> decode(String content) throws IOException {
        final Map<String, Object> ordered = new LinkedHashMap<>();
        // that load() populates via put() is an implementation detail rather than a documented
        // guarantee, so the sizes are compared below rather than trusting it
        final Properties properties = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                ordered.put((String) key, value);
                return super.put(key, value);
            }
        };
        properties.load(new StringReader(content));

        if (ordered.size() != properties.size()) {
            throw GenericException.formatted(
                "Read %d properties but only observed %d of them, so their order is unknown",
                properties.size(), ordered.size()
            );
        }
        return ordered;
    }

    @Override
    public String encode(Map<String, Object> content) throws IOException {
        content.forEach(PropertiesFileFormat::requireScalar);

        // store() is specified to write "in the natural sort order of the keys in entrySet()
        // unless entrySet() is overridden by a subclass", so an insertion ordered view of it
        // retains the original key order while the escaping stays with Properties
        final Properties properties = new Properties() {
            @Override
            public Set<Map.Entry<Object, Object>> entrySet() {
                final Set<Map.Entry<Object, Object>> ordered = new LinkedHashSet<>();
                content.forEach((key, value) ->
                    ordered.add(new AbstractMap.SimpleEntry<>(key, String.valueOf(value))));
                return ordered;
            }
        };

        final StringWriter out = new StringWriter();
        properties.store(out, null);

        // store() leads with the date, which java.properties.date can turn into several lines
        final String written = out.toString();
        int start = 0;
        while (start < written.length()
            && (written.charAt(start) == '#' || written.charAt(start) == '!')) {
            final int endOfLine = written.indexOf('\n', start);
            if (endOfLine < 0) {
                return "";
            }
            start = endOfLine + 1;
        }
        return written.substring(start);
    }

    private static void requireScalar(String key, Object value) {
        final String kind;
        if (value == null || (value instanceof JsonNode && ((JsonNode) value).isNull())) {
            kind = "null";
        } else if (value instanceof Map || value instanceof Collection
            || (value instanceof JsonNode && ((JsonNode) value).isContainerNode())) {
            kind = "structured";
        } else {
            return;
        }

        throw InvalidParameterException.formatted(
            "Properties files can only hold scalar values, but a %s value was given for '%s'",
            kind, key
        );
    }
}
