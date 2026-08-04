package me.itzg.helpers.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

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
            arguments("plain", "a", "b"),
            arguments("empty value", "a", ""),
            arguments("equals in value", "a", "x=y"),
            arguments("colon in value", "a", "host:24454"),
            arguments("hash in value", "a", "#not a comment"),
            arguments("exclamation in value", "a", "!not a comment"),
            arguments("backslash in value", "a", "C:\\path\\to"),
            arguments("newline in value", "a", "line1\nline2"),
            arguments("carriage return in value", "a", "line1\rline2"),
            arguments("tab in value", "a", "col1\tcol2"),
            arguments("leading space in value", "a", "   padded"),
            arguments("trailing space in value", "a", "padded   "),
            arguments("latin-1 in value", "a", "\u00A7cRed"),
            arguments("multi-byte in value", "a", "\u4e2d\u6587"),
            arguments("supplementary plane in value", "a", "\uD83D\uDE00 ok"),
            arguments("control character in value", "a", "x\u0001y"),
            arguments("equals in key", "a=b", "v"),
            arguments("colon in key", "a:b", "v"),
            arguments("space in key", "a b", "v"),
            arguments("hash in key", "#a", "v"),
            arguments("dot in key", "query.port", "25565"),
            arguments("hyphen in key", "max-players", "20"),
            arguments("backslash in key", "a\\b", "v"),
            arguments("multi-byte in key", "\u4e2d\u6587", "v")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("awkwardEntries")
    void roundTrips(String label, String key, String value) throws IOException {
        final Map<String, Object> original = Collections.singletonMap(key, value);

        final String encoded = format.encode(original);

        assertThat(format.decode(encoded))
            .as("encoded as:%n%s", encoded)
            .containsExactlyEntriesOf(original);
        assertThat(format.encode(format.decode(encoded)))
            .as("encoding is stable")
            .isEqualTo(encoded);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("awkwardEntries")
    void readsWhatPropertiesItselfWrites(String label, String key, String value) throws IOException {
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
            arguments("one line, lf", "#date\na=b\n", "a=b\n"),
            arguments("one line, crlf", "#date\r\na=b\r\n", "a=b\r\n"),
            arguments("several lines, lf", "#one\n#two\na=b\n", "a=b\n"),
            arguments("several lines, crlf", "#one\r\n#two\r\na=b\r\n", "a=b\r\n"),
            arguments("written with an exclamation", "!date\r\na=b\r\n", "a=b\r\n"),
            arguments("no entries follow", "#date\r\n", ""),
            arguments("unterminated", "#date", ""),
            arguments("an escaped hash never leads a line", "\\#a=b\r\n", "\\#a=b\r\n")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leadingComments")
    void stripsTheDateComment(String label, String written, String expected) {
        assertThat(PropertiesFileFormat.stripLeadingComments(written)).isEqualTo(expected);
    }

    public static Stream<Arguments> unsupportedValues() {
        final ObjectNode object = JsonNodeFactory.instance.objectNode();
        object.put("k", "v");
        final ArrayNode array = JsonNodeFactory.instance.arrayNode();
        array.add(1);

        return Stream.of(
            arguments("json object", object, "structured"),
            arguments("json array", array, "structured"),
            arguments("java list", Arrays.asList(1, 2), "structured"),
            arguments("java map", Collections.singletonMap("k", "v"), "structured"),
            arguments("json null", NullNode.getInstance(), "null"),
            arguments("java null", null, "null")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("unsupportedValues")
    void rejectsNonScalarValues(String label, Object value, String expectedKind) {
        final Map<String, Object> content = new LinkedHashMap<>();
        content.put("the-key", value);

        assertThatThrownBy(() -> format.encode(content))
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining(expectedKind)
            .hasMessageContaining("the-key");
    }
}
