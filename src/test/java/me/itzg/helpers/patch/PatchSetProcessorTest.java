package me.itzg.helpers.patch;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertJson;
import static uk.org.webcompere.modelassert.json.JsonAssertions.assertYaml;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import com.jayway.jsonpath.JsonPathException;
import me.itzg.helpers.env.EnvironmentVariablesProvider;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.patch.model.PatchAddOperation;
import me.itzg.helpers.patch.model.PatchDefinition;
import me.itzg.helpers.patch.model.PatchPutOperation;
import me.itzg.helpers.patch.model.PatchSet;
import me.itzg.helpers.patch.model.PatchSetOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatchSetProcessorTest {

    @Mock
    EnvironmentVariablesProvider environmentVariablesProvider;

    public static Stream<Arguments> setInJson_args() {
        return Stream.of(
            Arguments.arguments("testing.json", false),
            Arguments.arguments("testing.json", true),
            Arguments.arguments("testing-with-comment.json", true)
        );
    }

    @ParameterizedTest
    @MethodSource("setInJson_args")
    void setInJson(String file, boolean allowComments, @TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.json");
        Files.copy(Paths.get("src/test/resources/patch/" + file), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
            new Interpolator(environmentVariablesProvider, "CFG_"),
            allowComments
        );

        processor.process(new PatchSet()
            .setPatches(singletonList(
                new PatchDefinition()
                    .setFile(src.toString())
                    .setOps(singletonList(
                        new PatchSetOperation()
                            .setPath("$.outer.field1")
                            .setValue(new TextNode("new value"))
                    ))
            ))
        );

        assertThat(src).hasSameTextualContentAs(
            Paths.get("src/test/resources/patch/expected-setInJson.json")
        );
    }

    @Test
    void invalidPath(@TempDir Path tempDir) throws IOException {
        final Path original = Paths.get("src/test/resources/patch/testing.json");
        final Path src1 = tempDir.resolve("testing1.json");
        final Path src2 = tempDir.resolve("testing2.json");
        Files.copy(original, src1);
        Files.copy(original, src2);

        final PatchSetProcessor processor = new PatchSetProcessor(
            new Interpolator(environmentVariablesProvider, "CFG_")
        );

        assertThatThrownBy(() -> {
            processor.process(new PatchSet()
                .setPatches(Arrays.asList(
                    new PatchDefinition()
                        .setFile(src1.toString())
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new TextNode("new value"))
                        )),
                    new PatchDefinition()
                        .setFile(src2.toString())
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.invalid.path")
                                .setValue(new TextNode("na"))
                        ))
                ))
            );
        })
            .isInstanceOf(InvalidParameterException.class)
            .hasMessageContaining("testing2.json")
            .cause()
            .isInstanceOf(PatchOperationException.class)
            .hasMessageContaining("$.invalid.path")
            .hasRootCauseInstanceOf(JsonPathException.class);
    }

    @Test
    void addToArray(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.json");
        Files.copy(Paths.get("src/test/resources/patch/testing-with-array.json"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
            new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
            .setPatches(singletonList(
                new PatchDefinition()
                    .setFile(src.toString())
                    .setOps(singletonList(
                        new PatchAddOperation()
                            .setPath("$.outer.array")
                            .setValue(new TextNode("new value"))
                    ))
            ))
        );

        assertJson(src)
            .at("/outer/array/0").hasValue("new value");
    }

    @Test
    void setInJson5(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.json5");
        Files.copy(Paths.get("src/test/resources/patch/testing.json5"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(src.toString())
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new TextNode("new value"))
                        ))
                ))
        );

        assertThat(src).hasSameTextualContentAs(
                Paths.get("src/test/resources/patch/expected-setInJson5.json5")
        );
    }

    @Test
    void setInYaml(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.yaml");
        Files.copy(Paths.get("src/test/resources/patch/testing.yaml"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(src.toString())
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new TextNode("new value"))
                        ))
                ))
        );

        assertThat(src).hasSameTextualContentAs(
                Paths.get("src/test/resources/patch/expected-setInYaml.yaml")
        );
    }

    @Test
    void setInYamlWithUnderscores(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("limbo.yml");
        Files.copy(Paths.get("src/test/resources/patch/limbo.yml"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(src.toString())
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.main.prepare-min-version")
                                .setValue(new TextNode("1_21_11"))
                                .setValueType("string")
                        ))
                ))
        );

        assertThat(src).hasSameTextualContentAs(
                Paths.get("src/test/resources/patch/expected-limbo.yml")
        );
    }

    @Test
    void setInToml(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.toml");
        Files.copy(Paths.get("src/test/resources/patch/testing.toml"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(src.toString())
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new TextNode("new value"))
                        ))
                ))
        );

        assertThat(src).hasSameTextualContentAs(
                Paths.get("src/test/resources/patch/expected-setInToml.toml")
        );
    }

    @Test
    void setNativeTypes(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.yaml");
        Files.copy(Paths.get("src/test/resources/patch/testing.yaml"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(src.toString())
                        .setOps(Arrays.asList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new IntNode(5)),
                            new PatchSetOperation()
                                .setPath("$.outer.field2")
                                .setValue(new DoubleNode(5.1)),
                            new PatchSetOperation()
                                .setPath("$.outer.field3")
                                .setValue(BooleanNode.TRUE),
                            new PatchPutOperation()
                                .setPath("$.outer")
                                .setKey("field4")
                                .setValue(new ArrayNode(new JsonNodeFactory(true))
                                    .add(5).add(6).add(7)
                                )
                        ))
                ))
        );

        assertThat(src).hasSameTextualContentAs(
                Paths.get("src/test/resources/patch/expected-setNativeTypes.yaml")
        );
    }

    @Test
    void setWithEnv(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.yaml");
        Files.copy(Paths.get("src/test/resources/patch/testing.yaml"), src);

        when(environmentVariablesProvider.get("CFG_CUSTOM_FILE"))
                .thenReturn(null);
        when(environmentVariablesProvider.get("CFG_CUSTOM"))
                .thenReturn("value from env");
        when(environmentVariablesProvider.get("CFG_MISSING_FILE"))
                .thenReturn(null);
        when(environmentVariablesProvider.get("CFG_MISSING"))
                .thenReturn(null);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(src.toString())
                        .setOps(Arrays.asList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new TextNode("${CFG_CUSTOM}")),
                            new PatchSetOperation()
                                .setPath("$.outer.field2")
                                .setValue(new TextNode("${CFG_MISSING}")),
                            new PatchSetOperation()
                                .setPath("$.outer.field3")
                                .setValue(new TextNode("${GETS_IGNORED}"))
                        ))
                ))
        );

        assertThat(src).hasSameTextualContentAs(
                Paths.get("src/test/resources/patch/expected-setWithEnv.yaml")
        );

        verify(environmentVariablesProvider).get("CFG_CUSTOM_FILE");
        verify(environmentVariablesProvider).get("CFG_CUSTOM");
        verify(environmentVariablesProvider).get("CFG_MISSING_FILE");
        verify(environmentVariablesProvider).get("CFG_MISSING");
        verifyNoMoreInteractions(environmentVariablesProvider);
    }

    @Test
    void resolvesFileNameFromEnv(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.yaml");
        Files.write(src, Arrays.asList(
            "outer:",
            "  field1: original"
        ));

        when(environmentVariablesProvider.get("CFG_FILENAME_FILE"))
                .thenReturn(null);
        when(environmentVariablesProvider.get("CFG_FILENAME"))
                .thenReturn("testing.yaml");

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(singletonList(
                    new PatchDefinition()
                        .setFile(tempDir + "/${CFG_FILENAME}")
                        .setOps(singletonList(
                            new PatchSetOperation()
                                .setPath("$.outer.field1")
                                .setValue(new TextNode("updated"))
                        ))
                ))
        );

        assertYaml(src)
            .at("/outer/field1").hasValue("updated");

        verify(environmentVariablesProvider).get("CFG_FILENAME_FILE");
        verify(environmentVariablesProvider).get("CFG_FILENAME");
        verifyNoMoreInteractions(environmentVariablesProvider);
    }
}