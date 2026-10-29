package me.itzg.helpers.forge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.json.ObjectMappers;

public class ProvidedInstallerResolver implements InstallerResolver {

    public static final String PROP_ID = "id";
    public static final String PROP_INHERITS_FROM = "inheritsFrom";
    public static final String INSTALLER_ID_FORGE = "forge";
    public static final String INSTALLER_ID_NEOFORGE = "neoforge";
    public static final String INSTALLER_ID_CLEANROOM = "cleanroom";

    private final Path forgeInstaller;

    public ProvidedInstallerResolver(Path forgeInstaller) {
        this.forgeInstaller = forgeInstaller;
    }

    @Override
    public VersionPair resolve(ForgeManifest prevManifest) {
        final VersionPair versions;
        try {
            versions = InstallerVersionExtractor.extract(forgeInstaller);
        } catch (IOException e) {
            throw new GenericException("Failed to extract version from provided installer file", e);
        }
        if (versions == null) {
            throw new GenericException("Failed to locate version from provided installer file");
        }

        return versions;
    }

    @Override
    public Path download(String minecraftVersion, String forgeVersion, Path outputDir) {
        return forgeInstaller;
    }

    @Override
    public void cleanup(Path forgeInstallerJar) {
        // nothing needed
    }

    @Override
    public String getDescription() {
        return String.format("Provided installer %s", forgeInstaller.toString());
    }

    /**
     * Extract version from installer jar's version.json file where top level "id" and "inheritedFrom" fields are used
     * @throws GenericException if something wasn't right about the version.json
     */
    public static VersionPair extractFromVersionJson(InputStream versionJsonIn) throws IOException {
        final ObjectNode parsed = ObjectMappers.defaultMapper()
            .readValue(versionJsonIn, ObjectNode.class);

        final String id = parsed.get(PROP_ID).asText();
        final JsonNode inheritsFromNode = parsed.get(PROP_INHERITS_FROM);
        // TODO: Temp fix for Cleanroom until new helper for Cleanroom implemented
        // Reason: New cleanroom installer version.json file do not have inheritFrom to prevent LWJGL2 from downloaded
        if (!id.contains("Cleanroom") && (inheritsFromNode == null || inheritsFromNode.isMissingNode())) {
            throw new GenericException("Installer version.json is missing " + PROP_INHERITS_FROM);
        }
        // TODO: Temp fix for Cleanroom until new helper for Cleanroom implemented
        final String minecraftVersion = id.contains("Cleanroom") ? "1.12.2" : inheritsFromNode.asText();

        return IdResolver.buildVersionFromId(id, minecraftVersion);
    }

}
