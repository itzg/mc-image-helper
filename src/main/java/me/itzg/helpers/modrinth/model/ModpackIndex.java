package me.itzg.helpers.modrinth.model;

import lombok.Data;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * See <a href="https://docs.modrinth.com/docs/modpacks/format_definition/">spec</a>
 */
@Data
public class ModpackIndex {
    int formatVersion;
    String game;
    String versionId;
    String name;
    String summary;
    List<ModpackFile> files;
    Map<DependencyId, String> dependencies;

    @Data
    public static class ModpackFile {
        String path;
        Map<String,String> hashes;
        Map<Env, EnvType> env;
        List<URI> downloads;
        long fileSize;
    }
}
