package me.itzg.helpers.modrinth;

import java.util.List;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;

class VersionEvaluator {
    static Version pickVersion(Project project, List<Version> versions, VersionTypePref versionTypePref) {
        if (versionTypePref.isPrefersBest()) {
            for (int i = 0; i < versionTypePref.getVersionType().ordinal(); i++) {
                final VersionType versionTypeCandidate = VersionType.values()[i];
                for (final Version version : versions) {
                    if (versionTypeCandidate.equals(version.getVersionType())) {
                        return version;
                    }
                }
            }
        }
        else {
            for (final Version version : versions) {
                if (version.getVersionType().sufficientFor(versionTypePref.getVersionType())) {
                    return version;
                }
            }
        }

        if (!versions.isEmpty()) {
            throw new NoApplicableVersionsException(project, versions, versionTypePref);
        }
        return null;
    }

}
