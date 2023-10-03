package me.itzg.helpers.forge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.files.IoStreams;
import me.itzg.helpers.json.ObjectMappers;

public class ProvidedInstallerResolver implements InstallerResolver {
    private static final Pattern OLD_FORGE_ID_VERSION = Pattern.compile("forge(.+)", Pattern.CASE_INSENSITIVE);

    private final Path forgeInstaller;

    public ProvidedInstallerResolver(Path forgeInstaller) {
        this.forgeInstaller = forgeInstaller;
    }

    @Override
    public VersionPair resolve() {
        final VersionPair versions;
        try {
            versions = extractVersion(forgeInstaller);
        } catch (IOException e) {
            throw new GenericException("Failed to extract version from provided installer file", e);
        }
        if (versions == null) {
            throw new GenericException("Failed to locate version from provided installer file");
        }

        return new VersionPair(versions.minecraft, versions.forge);
    }

    @Override
    public Path download(String minecraftVersion, String forgeVersion, Path outputDir) {
        return forgeInstaller;
    }

    @Override
    public void cleanup(Path forgeInstallerJar) {
        // nothing needed
    }

    private VersionPair extractVersion(Path forgeInstaller) throws IOException {

        // Extract version from installer jar's version.json file
        // where top level "id" field is used

        final VersionPair fromVersionJson = IoStreams.readFileFromZip(forgeInstaller, "version.json", inputStream -> {
            final ObjectNode parsed = ObjectMappers.defaultMapper()
                .readValue(inputStream, ObjectNode.class);

            final String id = parsed.get("id").asText("");

            final String[] idParts = id.split("-");
            if (idParts.length != 3 || !idParts[1].equals("forge")) {
                throw new GenericException("Unexpected format of id from Forge installer's version.json: " + id);
            }

            return new VersionPair(idParts[0], idParts[2]);
        });
        if (fromVersionJson != null) {
            return fromVersionJson;
        }

        return IoStreams.readFileFromZip(forgeInstaller, "install_profile.json", inputStream -> {
            final ObjectNode parsed = ObjectMappers.defaultMapper()
                .readValue(inputStream, ObjectNode.class);

            final JsonNode idNode = parsed.path("versionInfo").path("id");
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
}
