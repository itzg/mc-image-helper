package me.itzg.helpers.patch;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PatchCommandTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void loadsPatchDefinitionOrPatchSetFile(boolean wrapInPatchSet, @TempDir Path tempDir)
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

        final JsonNode input = wrapInPatchSet
            ? objectMapper.createObjectNode()
                .set("patches", objectMapper.createArrayNode().add(definition))
            : definition;
        final Path patchFile = tempDir.resolve("patch.json");
        objectMapper.writeValue(patchFile.toFile(), input);

        final PatchCommand command = new PatchCommand();
        command.envPrefix = "CFG_";
        command.jsonAllowComments = true;
        command.patches = patchFile;

        assertThat(command.call()).isZero();
        assertJson(target).at("/name").hasValue("after");
    }
}
