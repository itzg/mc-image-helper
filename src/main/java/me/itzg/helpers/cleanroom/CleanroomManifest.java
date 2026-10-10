package me.itzg.helpers.cleanroom;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class CleanroomManifest extends BaseManifest {

    public static final String DEFAULT_MAVEN_URL = "https://maven.cleanroommc.com/";

    public static final String MAVEN_GROUP_ID = "com.cleanroommc";
    public static final String MAVEN_ARTIFACT_ID = "installer";

    public static final String MANIFEST_ID = "cleanroom";

    String installerVersion;
    String loaderVersion;

    /**
     * absolute path to jar file or run script
     */
    String serverEntry;

}

