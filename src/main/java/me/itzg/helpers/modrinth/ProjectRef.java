package me.itzg.helpers.modrinth;

import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.modrinth.model.VersionType;

import java.util.regex.Pattern;

@ToString
public class ProjectRef {
    private static final Pattern VERSIONS = Pattern.compile("[a-zA-z0-9]{8}");

    @Getter
    final String idOrSlug;
    @Getter
    final VersionType versionType;
    @Getter
    final String versionId;
    @Getter
    final String versionName;

    public static ProjectRef parse(String projectRef) {
        final String[] projectRefParts = projectRef.split(":", 2);

        return new ProjectRef(projectRefParts[0],
            projectRefParts.length > 1 ? projectRefParts[1] : null
        );
    }

    /**
     *
     * @param version can be a {@link VersionType}, ID, or name/number
     */
    public ProjectRef(String projectSlug, String version) {
        this.idOrSlug = projectSlug;
        this.versionType = parseVersionType(version);
        if (this.versionType == null) {
            if (isVersionId(version)) {
                this.versionId = version;
                this.versionName = null;
            }
            else {
                this.versionId = null;
                this.versionName = version;
            }
        }
        else {
            this.versionId = null;
            this.versionName = null;
        }
    }

    private boolean isVersionId(String version) {
        if (version == null) {
            return false;
        }
        return VERSIONS.matcher(version).matches();
    }

    private VersionType parseVersionType(String version) {
        if (version == null) {
            return null;
        }
        try {
            return VersionType.valueOf(version);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean hasVersionName() {
        return versionName != null;
    }

    public boolean hasVersionType() {
        return versionType != null;
    }

    public boolean hasVersionId() {
        return versionId != null;
    }
}
