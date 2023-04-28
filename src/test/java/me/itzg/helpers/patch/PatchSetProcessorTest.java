package me.itzg.helpers.patch;

import com.fasterxml.jackson.databind.node.*;
import me.itzg.helpers.env.EnvironmentVariablesProvider;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.patch.model.PatchDefinition;
import me.itzg.helpers.patch.model.PatchPutOperation;
import me.itzg.helpers.patch.model.PatchSet;
import me.itzg.helpers.patch.model.PatchSetOperation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatchSetProcessorTest {

    @Mock
    EnvironmentVariablesProvider environmentVariablesProvider;

    @Test
    void setInJson(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.json");
        Files.copy(Paths.get("src/test/resources/patch/testing.json"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(Arrays.asList(
                        new PatchDefinition()
                                .setFile(src)
                                .setOps(Arrays.asList(
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
    void setInJson5(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.json5");
        Files.copy(Paths.get("src/test/resources/patch/testing.json5"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(Arrays.asList(
                        new PatchDefinition()
                                .setFile(src)
                                .setOps(Arrays.asList(
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
                .setPatches(Arrays.asList(
                        new PatchDefinition()
                                .setFile(src)
                                .setOps(Arrays.asList(
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
    void setNativeTypes(@TempDir Path tempDir) throws IOException {
        final Path src = tempDir.resolve("testing.yaml");
        Files.copy(Paths.get("src/test/resources/patch/testing.yaml"), src);

        final PatchSetProcessor processor = new PatchSetProcessor(
                new Interpolator(environmentVariablesProvider, "CFG_")
        );

        processor.process(new PatchSet()
                .setPatches(Arrays.asList(
                        new PatchDefinition()
                                .setFile(src)
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
                .setPatches(Arrays.asList(
                        new PatchDefinition()
                                .setFile(src)
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
}