package me.itzg.helpers.patch;

import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class YamlFileFormat extends ObjectMapperFileFormat {

    public YamlFileFormat() {
        super(new YAMLMapper()
            .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
            .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true),
            "yaml",
            "yaml", "yml"
            );
    }

}
