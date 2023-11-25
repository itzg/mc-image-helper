package me.itzg.helpers.patch;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;

public class TomlFileFormat extends ObjectMapperFileFormat {

    public TomlFileFormat() {
        super(new TomlMapper(), "toml", "toml");
    }
}
