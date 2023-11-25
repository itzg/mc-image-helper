package me.itzg.helpers.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.CharsetDetector;
import me.itzg.helpers.env.Interpolator;
import me.itzg.helpers.env.Interpolator.Result;
import me.itzg.helpers.env.MissingVariablesException;
import me.itzg.helpers.patch.model.PatchDefinition;
import me.itzg.helpers.patch.model.PatchOperation;
import me.itzg.helpers.patch.model.PatchPutOperation;
import me.itzg.helpers.patch.model.PatchSet;
import me.itzg.helpers.patch.model.PatchSetOperation;

@Slf4j
public class PatchSetProcessor {

    private final Interpolator interpolator;

    public PatchSetProcessor(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    private final FileFormat[] fileFormats = new FileFormat[]{
            new JsonFileFormat(),
            new Json5FileFormat(),
            new YamlFileFormat(),
            new TomlFileFormat()
    };

    public void process(PatchSet patchSet) {
        log.debug("patchSet={}", patchSet);

        for (PatchDefinition patch : patchSet.getPatches()) {
            processPatch(patch);
        }
    }

    private void processPatch(PatchDefinition patch) {
        final Path filePath = resolveFilePath(patch.getFile());

        if (Files.isRegularFile(filePath)) {
            final FileFormat fileFormat = resolveFileFormat(patch, filePath);

            if (fileFormat != null) {
                log.debug("Detected file={} is {}", filePath, fileFormat.getName());

                try {
                    final byte[] rawContent = Files.readAllBytes(filePath);
                    final CharsetDetector.Result detected = CharsetDetector.detect(rawContent);

                    final Map<String, Object> data = fileFormat.decode(detected.getContent().toString());
                    if (patch.getOps() != null && !patch.getOps().isEmpty()) {
                        applyOps(data, patch.getOps());

                        try (OutputStream out = Files.newOutputStream(filePath)) {
                            out.write(
                                    fileFormat
                                            .encode(data)
                                            .getBytes(detected.getCharset())
                            );
                        }
                    }
                } catch (IOException e) {
                    log.warn("Failed to read content of {}: {}", filePath, e.getMessage());
                    log.debug("Details", e);
                }
            }
        } else {
            log.warn("{} is not an existing file", filePath);
        }
    }

    private Path resolveFilePath(String file) {
        try {
            final Result<String> fileResult = interpolator.interpolate(file);
            return Paths.get(fileResult.getContent());
        } catch (IOException e) {
            log.warn("Failed to interpolate file name from patch", e);
            return Paths.get(file);
        }

    }

    private FileFormat resolveFileFormat(PatchDefinition patch, Path filePath) {
        final String formatName = patch.getFileFormat();
        final FileFormat fileFormat;
        if (formatName != null) {
            fileFormat = getFileFormatByName(formatName);
            log.warn("The file format {} is not supported", formatName);
        } else {
            fileFormat = findFileFormat(filePath);
            if (fileFormat == null) {
                log.warn("The file format of {} could not be identified", filePath);
            }
        }
        return fileFormat;
    }

    private FileFormat getFileFormatByName(String formatName) {
        for (FileFormat fileFormat : fileFormats) {
            if (fileFormat.getName().equals(formatName)) {
                return fileFormat;
            }
        }
        return null;
    }

    private FileFormat findFileFormat(Path filePath) {
        for (FileFormat fileFormat : fileFormats) {
            for (String suffix : fileFormat.getFileSuffixes()) {
                if (filePath.getFileName().toString().endsWith("." + suffix)) {
                    return fileFormat;
                }
            }
        }
        return null;
    }

    private void applyOps(Map<String, Object> data, List<PatchOperation> ops) throws IOException {
        final DocumentContext doc = JsonPath.parse(data);

        for (PatchOperation op : ops) {
            if (op instanceof PatchSetOperation) {
                final PatchSetOperation setOp = (PatchSetOperation) op;
                processValueType(
                        setOp.getValue(), setOp.getValueType(),
                        "set", setOp.getPath(),
                        obj -> doc.set(setOp.getPath(), obj)
                );
            } else if (op instanceof PatchPutOperation) {
                final PatchPutOperation putOp = (PatchPutOperation) op;
                processValueType(
                        putOp.getValue(), putOp.getValueType(),
                        "put", putOp + " at " + putOp.getPath(),
                        obj -> doc.put(putOp.getPath(), putOp.getKey(), obj)
                );
            }
        }
    }

    private void processValueType(JsonNode value, String valueType, String opName, String path, Consumer<Object> consumer) throws IOException {
        if (value instanceof TextNode) {
            try {
                processTextNode((TextNode) value, valueType, consumer);
            } catch (MissingVariablesException e) {
                log.warn("Unable to " + opName + " '{}' due to missing environment variables: {}", path, e.getVariables());
            }
        } else {
            consumer.accept(value);
        }

    }

    private void processTextNode(TextNode value, String valueType,
                                 Consumer<Object> consumer) throws IOException {
        final Interpolator.Result<String> interpolateResult = interpolator.interpolate(value.textValue());

        if (interpolateResult.getMissingVariables().isEmpty()) {
            final ValueTypeConverter valueTypeConverter = new ValueTypeConverter(valueType);

            final Object convertedValue = valueTypeConverter.convert(interpolateResult.getContent());

            log.debug("Interpolated value={} into {} and converted into type={}",
                    value, interpolateResult.getContent(), convertedValue.getClass());

            consumer.accept(convertedValue);
        }
        else {
            throw new MissingVariablesException(interpolateResult.getMissingVariables());
        }
    }

}
