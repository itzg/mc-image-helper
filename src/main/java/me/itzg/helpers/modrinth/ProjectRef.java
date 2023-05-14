package me.itzg.helpers.modrinth;

import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.modrinth.model.VersionType;

@ToString
public class ProjectRef {
    @Getter
    final String idOrSlug;
    final String versionIdOrType;
    @Getter
    final String versionName;

    public static ProjectRef parse(String projectRef) {
        final String[] projectRefParts = projectRef.split(":", 2);

        return new ProjectRef(projectRefParts[0],
            projectRefParts.length > 1 ? projectRefParts[1] : null,
            null
        );
    }

    public ProjectRef(String projectSlug, String versionIdOrType, String versionName) {
        this.idOrSlug = projectSlug;
        this.versionIdOrType = versionIdOrType;
        this.versionName = versionName;
    }

    public boolean hasVersionName() {
        return versionName != null;
    }

    public boolean hasVersionType() {
        if (versionIdOrType != null) {
            try {
                VersionType.valueOf(versionIdOrType);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    public VersionType getVersionType() {
        return VersionType.valueOf(versionIdOrType);
    }

    public boolean hasVersionId() {
        return versionIdOrType != null &&
            !hasVersionType();
    }

    public String getVersionId() {
        return versionIdOrType;
    }
}
