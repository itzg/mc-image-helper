package me.itzg.helpers.modrinth.pack;

import java.nio.file.Path;

import lombok.Data;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.ModpackLoader;
import me.itzg.helpers.modrinth.ModrinthModpackManifest;
import me.itzg.helpers.modrinth.model.ModpackIndex;
import me.itzg.helpers.modrinth.model.VersionType;

public class ModrinthPack {
    ModpackIndex index;
    ModrinthModpackManifest prevManifest, newManifest;
    Config config;

    IModrinthPackFetcher packFetcher;
    // ModrinthPackInstaller packInstaller;

    public ModrinthPack(ModrinthPack.Config config) {
        this.config = config;
        this.packFetcher = new ModrinthApiPackFetcher(config);
        // this.packInstaller = new ModrinthPackInstaller();
        this.prevManifest = Manifests.load(
            config.outputDirectory, ModrinthModpackManifest.ID, ModrinthModpackManifest.class);
    }


    public void install() {
        this.packFetcher.fetchModpack(prevManifest);
    }

    @Data
    public static class Config {
        String project;
        String version;
        String apiBaseUrl;
        ModpackLoader loader;
        String gameVersion;
        VersionType defaultVersionType;
        Path outputDirectory;
        Boolean forceSynchronize;
        SharedFetchArgs sharedFetchArgs;
    }
}