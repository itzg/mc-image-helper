package me.itzg.helpers.modrinth;

import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.modrinth.model.VersionType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ToString
public class ProjectRef {
    private static final Pattern VERSIONS = Pattern.compile("[a-zA-Z0-9]{8}");
    private final static Pattern MODPACK_PAGE_URL = Pattern.compile(
        "https://modrinth.com/modpack/(?<slug>.+?)(/version/(?<versionName>.+))?"
    );

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

    public static ProjectRef fromPossibleUrl(
            String projectUrl, String defaultVersion)
    {
        final Matcher m = MODPACK_PAGE_URL.matcher(projectUrl);
        String projectSlug = m.matches() ? m.group("slug") : projectUrl;
        String projectVersion = m.matches() && m.group("versionName") != null ?
            m.group("versionName") : defaultVersion;

        return new ProjectRef(projectSlug, projectVersion);
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

    private boolean isVersionId(String version) {
        return version == null ? false : VERSIONS.matcher(version).matches();
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
}
