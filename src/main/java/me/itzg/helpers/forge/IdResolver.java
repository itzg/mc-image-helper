package me.itzg.helpers.forge;

import me.itzg.helpers.errors.GenericException;

public class IdResolver {

    static VersionPair buildVersionFromId(String id, String minecraftVersion) {
        final String[] idParts = id.split("-");
        if (idParts.length >= 3) {
            if (idParts[1].equals(ProvidedInstallerResolver.INSTALLER_ID_FORGE)) {
                return new VersionPair(minecraftVersion, idParts[2]);
            }
            if (idParts[0].equals(ProvidedInstallerResolver.INSTALLER_ID_NEOFORGE)) {
                return new VersionPair(minecraftVersion, String.join("-", idParts[1], idParts[2]));
            }
            if (idParts[0].equals(ProvidedInstallerResolver.INSTALLER_ID_CLEANROOM)) {
                return new VersionPair(minecraftVersion, String.join("-", idParts[1], idParts[2]))
                    .setVariantOverride(ProvidedInstallerResolver.INSTALLER_ID_CLEANROOM);
            }
        } else if (idParts.length >= 2) {
            if (idParts[0].equals(ProvidedInstallerResolver.INSTALLER_ID_NEOFORGE)) {
                return new VersionPair(minecraftVersion, idParts[1]);
            }
        }

        throw new GenericException("Unexpected format of id from Forge installer's version.json: " + id);
    }
}
