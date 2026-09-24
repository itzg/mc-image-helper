package me.itzg.helpers.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PatchCommandTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static Stream<Arguments> loadsPatchDefinitionOrPatchSetFile_args() {
        return Stream.of(
            Arguments.of(false, false),
            Arguments.of(true, false),
            Arguments.of(false, true),
            Arguments.of(true, true)
        );
    }

    @ParameterizedTest
    @MethodSource("loadsPatchDefinitionOrPatchSetFile_args")
    void loadsPatchDefinitionOrPatchSetFile(boolean wrapInPatchSet, boolean addSchemaField, @TempDir Path tempDir)
            throws Exception {
        final Path target = tempDir.resolve("target.json");
        Files.writeString(target, "{\"name\":\"before\"}");

        final ObjectNode definition = objectMapper.createObjectNode();
        definition.put("file", target.toString());
        definition.putArray("ops")
            .addObject()
            .putObject("$set")
            .put("path", "$.name")
            .put("value", "after");

        final ObjectNode patchOrPatchSet = wrapInPatchSet
            ? objectMapper.createObjectNode().set("patches", objectMapper.createArrayNode().add(definition))
            : definition;

        if  (addSchemaField) {
            // the specific url doesn't matter, just testing if adding the field breaks intended the behavior
            patchOrPatchSet.put("$schema", "https://itzg.github.io/mc-image-helper/schemas/patch-set.jsonc");
        }

        final Path patchFile = tempDir.resolve("patch.json");
        objectMapper.writeValue(patchFile.toFile(), patchOrPatchSet);

        final PatchCommand command = new PatchCommand();
        command.envPrefix = "CFG_";
        command.jsonAllowComments = true;
        command.patches = patchFile;

        assertThat(command.call()).isZero();
        assertJson(target).at("/name").hasValue("after");
    }
}
