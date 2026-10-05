package me.itzg.helpers.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import me.itzg.helpers.errors.InvalidParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PropertiesFileFormatTest {

    private final PropertiesFileFormat format = new PropertiesFileFormat();

    public static Stream<Arguments> awkwardEntries() {
        return Stream.of(
            argumentSet("plain", "a", "b"),
            argumentSet("empty value", "a", ""),
            argumentSet("equals in value", "a", "x=y"),
            argumentSet("colon in value", "a", "host:24454"),
            argumentSet("hash in value", "a", "#not a comment"),
            argumentSet("exclamation in value", "a", "!not a comment"),
            argumentSet("backslash in value", "a", "C:\\path\\to"),
            argumentSet("newline in value", "a", "line1\nline2"),
            argumentSet("carriage return in value", "a", "line1\rline2"),
            argumentSet("tab in value", "a", "col1\tcol2"),
            argumentSet("leading space in value", "a", "   padded"),
            argumentSet("trailing space in value", "a", "padded   "),
            argumentSet("latin-1 in value", "a", "\u00A7cRed"),
            argumentSet("multi-byte in value", "a", "\u4e2d\u6587"),
            argumentSet("supplementary plane in value", "a", "\uD83D\uDE00 ok"),
            argumentSet("control character in value", "a", "x\u0001y"),
            argumentSet("equals in key", "a=b", "v"),
            argumentSet("colon in key", "a:b", "v"),
            argumentSet("space in key", "a b", "v"),
            argumentSet("hash in key", "#a", "v"),
            argumentSet("dot in key", "query.port", "25565"),
            argumentSet("hyphen in key", "max-players", "20"),
            argumentSet("backslash in key", "a\\b", "v"),
            argumentSet("multi-byte in key", "\u4e2d\u6587", "v")
        );
    }

    @ParameterizedTest
    @MethodSource("awkwardEntries")
    void roundTrips(String key, String value) throws IOException {
        final Map<String, Object> original = Collections.singletonMap(key, value);

        final String encoded = format.encode(original);

        assertThat(format.decode(encoded))
            .as("encoded as:%n%s", encoded)
            .containsExactlyEntriesOf(original);
        assertThat(format.encode(format.decode(encoded)))
            .as("encoding is stable")
            .isEqualTo(encoded);
    }

    @ParameterizedTest
    @MethodSource("awkwardEntries")
    void readsWhatPropertiesItselfWrites(String key, String value) throws IOException {
        final Properties properties = new Properties();
        properties.setProperty(key, value);
        final StringWriter written = new StringWriter();
        properties.store(written, null);

        assertThat(format.decode(written.toString())).containsEntry(key, value);
    }

    @Test
    void retainsKeyOrder() throws IOException {
        final Map<String, Object> original = new LinkedHashMap<>();
        original.put("zz", "1");
        original.put("aa", "2");
        original.put("mm", "3");

        assertThat(format.encode(original))
            .isEqualToNormalizingNewlines("zz=1\naa=2\nmm=3\n");
    }

    public static Stream<Arguments> leadingComments() {
        return Stream.of(
            argumentSet("one line, lf", "#date\na=b\n", "a=b\n"),
            argumentSet("one line, crlf", "#date\r\na=b\r\n", "a=b\r\n"),
            argumentSet("several lines, lf", "#one\n#two\na=b\n", "a=b\n"),
            argumentSet("several lines, crlf", "#one\r\n#two\r\na=b\r\n", "a=b\r\n"),
            argumentSet("written with an exclamation", "!date\r\na=b\r\n", "a=b\r\n"),
            argumentSet("no entries follow", "#date\r\n", ""),
            argumentSet("unterminated", "#date", ""),
            argumentSet("an escaped hash never leads a line", "\\#a=b\r\n", "\\#a=b\r\n")
        );
    }

    @ParameterizedTest
    @MethodSource("leadingComments")
    void stripsTheDateComment(String written, String expected) {
        assertThat(PropertiesFileFormat.stripLeadingComments(written)).isEqualTo(expected);
    }

    public static Stream<Arguments> unsupportedValues() {
        final ObjectNode object = JsonNodeFactory.instance.objectNode();
        object.put("k", "v");
        final ArrayNode array = JsonNodeFactory.instance.arrayNode();
        array.add(1);

        return Stream.of(
            argumentSet("json object", object, "structured"),
            argumentSet("json array", array, "structured"),
            argumentSet("java list", Arrays.asList(1, 2), "structured"),
            argumentSet("java map", Collections.singletonMap("k", "v"), "structured"),
            argumentSet("json null", NullNode.getInstance(), "null"),
            argumentSet("java null", null, "null")
        );
    }

    @ParameterizedTest
    @MethodSource("unsupportedValues")
    void rejectsNonScalarValues(Object value, String expectedKind) {
        final Map<String, Object> content = new LinkedHashMap<>();
        content.put("the-key", value);

        assertThatThrownBy(() -> format.encode(content))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining(expectedKind)
            .hasMessageContaining("the-key");
    }
}
