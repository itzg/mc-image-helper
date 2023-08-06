package me.itzg.helpers.modrinth;

import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.modrinth.model.VersionType;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ToString
public class ProjectRef {
    private static final Pattern VERSIONS = Pattern.compile("[a-zA-Z0-9]{8}");
    private final static Pattern MODPACK_PAGE_URL = Pattern.compile(
        "https://modrinth.com/modpack/(?<slug>.+?)(/version/(?<versionName>.+))?"
    );
    private final static Pattern HOMEBREW_MRPACK = Pattern.compile(
        ".+/(?<slug>\\w+?)\\.mrpack"
    );

    @Getter
    final String idOrSlug;
    @Getter
    final URI projectUri;
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
        this.projectUri = null;
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

    public ProjectRef(URI projectUri, String versionId) {
        this.projectUri = projectUri;
        final Matcher m = HOMEBREW_MRPACK.matcher(projectUri.toString());
        this.idOrSlug = m.matches() ? m.group("slug") : "boo";
    
        this.versionName = null;
        this.versionType = null;
        this.versionId = versionId;
    }

    public static ProjectRef fromPossibleUrl(
            String possibleUrl, String defaultVersion)
    {
        final Matcher m = MODPACK_PAGE_URL.matcher(possibleUrl);
        if(m.matches()) {
            String projectSlug = m.group("slug");
            String projectVersion = m.group("versionName") != null ?
                m.group("versionName") : defaultVersion;
            return new ProjectRef(projectSlug, projectVersion);
        } else {
            try {
                return new ProjectRef(
                    new URL(possibleUrl).toURI(), defaultVersion);
            } catch(MalformedURLException | URISyntaxException e) {
                return new ProjectRef(possibleUrl, defaultVersion);
            }
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

    public boolean hasProjectUri() {
        return projectUri != null;
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
