package me.itzg.helpers.forge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.json.ObjectMappers;

public class ProvidedInstallerResolver implements InstallerResolver {

    private static final Pattern OLD_FORGE_ID_VERSION = Pattern.compile("forge(.+)", Pattern.CASE_INSENSITIVE);
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
            versions = extractVersion(forgeInstaller);
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

    private VersionPair extractVersion(Path forgeInstaller) throws IOException {

        final VersionPair fromVersionJson = IoStreams.readFileFromZip(forgeInstaller, "version.json",
            ProvidedInstallerResolver::extractFromVersionJson
        );
        // will be null if version.json wasn't present
        if (fromVersionJson != null) {
            return fromVersionJson;
        }

        return IoStreams.readFileFromZip(forgeInstaller, "install_profile.json", inputStream -> {
            final ObjectNode parsed = ObjectMappers.defaultMapper()
                .readValue(inputStream, ObjectNode.class);

            final JsonNode idNode = parsed.path("versionInfo").path(PROP_ID);
            if (idNode.isTextual()) {
                final String[] idParts = idNode.asText().split("-");

                if (idParts.length >= 2) {
                    final Matcher m = OLD_FORGE_ID_VERSION.matcher(idParts[1]);
                    if (m.matches()) {
                        if (m.group(1).equals(idParts[0])) {
                            // such as 1.11.2-forge1.11.2-13.20.1.2588
                            return new VersionPair(idParts[0], idParts[2]);
                        }
                        else {
                            // such as 1.7.10-Forge10.13.4.1614-1.7.10
                            return new VersionPair(idParts[0], m.group(1));
                        }
                    }
                    else {
                        throw new GenericException("Unexpected format of id from Forge installer's install_profile.json: " + idNode.asText());
                    }
                }
                else {
                    throw new GenericException("Unexpected format of id from Forge installer's install_profile.json: " + idNode.asText());
                }
            }
            else {
                throw new GenericException("install_profile.json seems to be missing versionInfo.id");

            }
        });
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
        if (inheritsFromNode.isMissingNode()) {
            throw new GenericException("Installer version.json is missing " + PROP_INHERITS_FROM);
        }
        final String minecraftVersion = inheritsFromNode.asText();

        final String[] idParts = id.split("-");
        if (idParts.length >= 3) {
            if (idParts[1].equals(INSTALLER_ID_FORGE)) {
                return new VersionPair(minecraftVersion, idParts[2]);
            }
            if (idParts[0].equals(INSTALLER_ID_NEOFORGE)) {
                return new VersionPair(minecraftVersion, idParts[1]);
            }
            if (idParts[0].equals(INSTALLER_ID_CLEANROOM)) {
                return new VersionPair(minecraftVersion, String.join("-", idParts[1], idParts[2]))
                    .setVariantOverride(INSTALLER_ID_CLEANROOM);
            }
        }

        throw new GenericException("Unexpected format of id from Forge installer's version.json: " + id);
    }
}