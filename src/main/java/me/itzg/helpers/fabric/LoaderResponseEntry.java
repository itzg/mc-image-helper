package me.itzg.helpers.fabric;

import lombok.Data;

@Data
public class LoaderResponseEntry {

    Loader loader;

    @Data
    public static class Loader {
        boolean stable;
        String version;
    }
}
