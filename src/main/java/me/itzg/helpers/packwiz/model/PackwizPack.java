package me.itzg.helpers.packwiz.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public final class PackwizPack {
    private String name;
    private String author;
    private String version;

    private Map<String, String> versions = new HashMap<>();
}
