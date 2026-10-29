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

/**
 * Reads Minecraft and loader versions out of a Forge-style installer jar.
 */
final class InstallerVersionExtractor {

    private static final Pattern OLD_FORGE_ID_VERSION = Pattern.compile("forge(.+)", Pattern.CASE_INSENSITIVE);

    private InstallerVersionExtractor() {
    }

    static VersionPair extract(Path installerJar) throws IOException {
        final VersionPair fromVersionJson = IoStreams.readFileFromZip(installerJar, "version.json",
            ProvidedInstallerResolver::extractFromVersionJson
        );
        // will be null if version.json wasn't present
        if (fromVersionJson != null) {
            return fromVersionJson;
        }

        return IoStreams.readFileFromZip(installerJar, "install_profile.json", inputStream -> {
            final ObjectNode parsed = ObjectMappers.defaultMapper()
                .readValue(inputStream, ObjectNode.class);

            final JsonNode idNode = parsed.path("versionInfo").path(ProvidedInstallerResolver.PROP_ID);
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
