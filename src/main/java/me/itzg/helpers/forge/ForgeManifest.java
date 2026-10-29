package me.itzg.helpers.forge;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class ForgeManifest extends BaseManifest {
    String minecraftVersion;
    String forgeVersion;
    /**
     * Set when the installer came from {@code --forge-installer-url}.
     * Omitted for older manifests and for version or local-file installs.
     */
    @Getter(AccessLevel.NONE)
    String installerUrl;
    /**
     * absolute path to jar file or run script
     */
    String serverEntry;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getInstallerUrl() {
        return installerUrl;
    }
}
