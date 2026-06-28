package me.itzg.helpers.packwiz;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.fabric.FabricLauncherInstaller;
import me.itzg.helpers.files.Manifests;
import me.itzg.helpers.files.ResultsFileWriter;
import me.itzg.helpers.forge.ForgeInstaller;
import me.itzg.helpers.forge.ForgeInstallerResolver;
import me.itzg.helpers.forge.NeoForgeInstallerResolver;
import me.itzg.helpers.http.Fetch;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.SharedFetchArgs;
import me.itzg.helpers.modrinth.ModrinthModpackManifest;
import me.itzg.helpers.mvn.MavenRepoApi;
import me.itzg.helpers.packwiz.model.PackwizPack;
import me.itzg.helpers.quilt.QuiltInstaller;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;
import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.Callable;

@Command(name = "install-packwiz-modpack",
    description = "Supports installation of Packwiz modpacks along with the associated mod loader",
    mixinStandardHelpOptions = true
)
@Slf4j
public final class InstallPackwizModpackCommand implements Callable<Integer> {
    @Option(names = "--pack", required = true, description = "The path or URL to the modpack's pack.toml")
    String pack;

    @Option(names = "--output-directory", defaultValue = ".", paramLabel = "DIR")
    Path outputDirectory;

    @Option(names = "--results-file", description = ResultsFileWriter.OPTION_DESCRIPTION, paramLabel = "FILE")
    Path resultsFile;

    @Option(names = "--force-update", defaultValue = "${env:PACKWIZ_FORCE_UPDATE:-false}",
        description = "Force update the pack even when the version hasn't changed (default: ${DEFAULT-VALUE})"
    )
    boolean forceUpdate;

    @ArgGroup(exclusive = false)
    SharedFetchArgs sharedFetchArgs = new SharedFetchArgs();

    @Override
    public Integer call() throws IOException {
        final PackwizModpackManifest prevManifest = Manifests.load(
            outputDirectory, PackwizModpackManifest.ID,
            PackwizModpackManifest.class
        );

        final PackwizModpackManifest newManifest;
        try (SharedFetch fetch = Fetch.sharedFetch("install-packwiz-modpack", sharedFetchArgs.options())) {
            newManifest = getManifest(fetch);
            if (prevManifest != null && prevManifest.getVersion().equals(newManifest.getVersion()) && !forceUpdate) {
                return ExitCode.OK;
            }

            final String minecraftVersion = newManifest.minecraftVersion();
            if (minecraftVersion == null) {
                throw new GenericException("Minecraft version not set in packwiz pack");
            }
            // TODO might need to set/save the Minecraft version somewhere

            installModLoader(newManifest, fetch);
            bootstrapPackwiz(fetch);

            // populate the results file (MODPACK_NAME, MODPACK_VERSION)
        }

        Manifests.cleanup(outputDirectory, prevManifest, newManifest, log);
        Manifests.save(outputDirectory, ModrinthModpackManifest.ID, newManifest);

        return ExitCode.OK;
    }

    private void bootstrapPackwiz(SharedFetch fetch) throws IOException {
        //     if ! packwizInstaller=$(mc-image-helper maven-download \
        // --maven-repo=https://maven.packwiz.infra.link/repository/release/ \
        // --group=link.infra.packwiz --artifact=packwiz-installer --classifier=dist \
        // --skip-existing); then
        //
        //
        //
        // java -cp "${packwizInstaller}" link.infra.packwiz.installer.Main -s server "${PACKWIZ_URL}"; then

        final MavenRepoApi mavenRepoApi = new MavenRepoApi("https://maven.packwiz.infra.link/repository/release/", fetch)
            .setMetadataCacheDir(outputDirectory.resolve(".maven"))
            .setSkipExisting(true)
            .setSkipUpToDate(false);

        final String group = "link.infra.packwiz";
        final String artifactId = "packwiz-installer";
        final Path installerJar = mavenRepoApi.fetchMetadata(group, artifactId)
            .flatMap(metadata -> mavenRepoApi.download(
                outputDirectory, group, artifactId, metadata.getVersioning().getRelease(), "jar", "dist"
            ))
            .block();

        final Process process = new ProcessBuilder(
            "java",
            "-cp", installerJar.toAbsolutePath().toString(),
            "link.infra.packwiz.installer.Main",
            "-s", "server",
            pack)
            .directory(outputDirectory.toFile())
            .redirectOutput(Redirect.INHERIT)
            .redirectError(Redirect.INHERIT)
            .start();
        // TODO check exit code
    }

    private void installModLoader(PackwizModpackManifest manifest, SharedFetch fetch) {
        final String minecraftVersion = manifest.minecraftVersion();
        // TODO support forceReinstall parameters (the falses)
        // check NeoForge and Forge first, in case the pack is using Fabric mods on (Neo)Forge via Sinytra Connector or similar
        // (packwiz requires you to declare a fabric version in your pack.toml to add fabric mods to a (neo)forge-based pack)
        final String neoforgeVersion = manifest.neoforgeVersion();
        if (neoforgeVersion != null) {
            new ForgeInstaller(new NeoForgeInstallerResolver(fetch, minecraftVersion, neoforgeVersion))
                .install(outputDirectory, resultsFile, false, "NeoForge");
            return;
        }

        final String forgeVersion = manifest.forgeVersion();
        if (forgeVersion != null) {
            new ForgeInstaller(new ForgeInstallerResolver(fetch, minecraftVersion, forgeVersion))
                .install(outputDirectory, resultsFile, false, "Forge");
            return;
        }

        final String fabricVersion = manifest.fabricVersion();
        if (fabricVersion != null) {
            new FabricLauncherInstaller(outputDirectory)
                .setResultsFile(resultsFile)
                .installUsingVersions(sharedFetchArgs.options(), minecraftVersion, fabricVersion, null);
            return;
        }

        final String quiltVersion = manifest.quiltVersion();
        if (quiltVersion != null) {
            try (QuiltInstaller installer = new QuiltInstaller(
                QuiltInstaller.DEFAULT_REPO_URL,
                sharedFetchArgs.options(),
                outputDirectory,
                minecraftVersion
            ).setResultsFile(resultsFile)) {
                installer.installWithVersion(null, quiltVersion);
            }
        }
    }

    private PackwizModpackManifest getManifest(SharedFetch fetch) throws IOException {
        URI uri;
        try {
            uri = new URI(pack);
        } catch (URISyntaxException e) {
            throw new InvalidParameterException("Could not parse packwiz modpack URI/path", e);
        }

        final PackwizPack packFile;
        if (uri.getScheme() == null || uri.getScheme().equals("file")) {
            log.debug("Fetching packwiz modpack from file {}", uri.getPath());
            packFile = new TomlMapper().readValue(new File(uri.getPath()), PackwizPack.class);
        } else {
            log.debug("Fetching packwiz modpack from URL {}", uri);
            packFile = fetch.fetch(uri).toObject(PackwizPack.class, new TomlMapper()).execute();
        }

        log.debug(
            "Found packwiz pack with name={}, author={}, version={}, dependencies={}",
            packFile.getName(),
            packFile.getAuthor(),
            packFile.getVersion(),
            packFile.getVersions()
            );

        return PackwizModpackManifest.builder()
            .name(packFile.getName())
            .author(packFile.getAuthor())
            .version(packFile.getVersion())
            .dependencies(packFile.getVersions())
            .files(Collections.emptyList())
            .build();
    }
}
