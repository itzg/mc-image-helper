package me.itzg.helpers.modrinth;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.modrinth.model.VersionType;
import org.jetbrains.annotations.Nullable;

@Getter
@ToString
public class ProjectRef {
    private static final Pattern VERSIONS = Pattern.compile("[a-zA-Z0-9]{8}");
    private static final Pattern MODPACK_PAGE_URL = Pattern.compile(
        "https://modrinth.com/modpack/(?<slug>.+?)(/version/(?<versionName>.+))?"
    );
    private static final Set<String> VALID_LOADERS = Arrays.stream(Loader.values())
        .map(Enum::name)
        .map(String::toLowerCase)
        .collect(Collectors.toSet());

    private final String idOrSlug;
    private final Loader loader;

    /**
     * Either a remote URI or a file URI for a locally provided file
     */
    private final URI projectUri;
    private final VersionType versionType;
    private final String versionId;
    private final String versionNumber;

    public static ProjectRef parse(String projectRef) {
        final String[] parts = projectRef.split(":", 3);
        
        // Handle cases with no colon
        if (parts.length == 1) {
            return new ProjectRef(parts[0], null, null);
        }
        
        // Check if first part is a valid loader
        Loader loader = null;
        int idIndex = 0;
        if (VALID_LOADERS.contains(parts[0].toLowerCase())) {
            loader = Loader.valueOf(parts[0].toLowerCase());
            idIndex = 1;
        }
        
        // Handle remaining parts
        if (parts.length == 2) {
            // Either loader:id or id:version
            if (loader != null) {
                return new ProjectRef(parts[1], null, loader);
            } else {
                return new ProjectRef(parts[0], parts[1], null);
            }
        } 
        else if (parts.length == 3) {
            // Must be loader:id:version
            if (loader == null) {
                throw new InvalidParameterException("Invalid loader in project reference: " + parts[0]);
            }
            return new ProjectRef(parts[1], parts[2], loader);
        }
        
        throw new InvalidParameterException("Invalid project reference: " + projectRef);
    }

    /**
     *
     * @param version can be a {@link VersionType}, ID, or name/number
     */
    public ProjectRef(String projectSlug, String version) {
        this(projectSlug, version, null);
    }

    /**
     * @param version  can be a {@link VersionType}, ID, or name/number
     */
    public ProjectRef(String projectSlug, @Nullable String version, Loader loader) {
        this.idOrSlug = projectSlug;
        this.loader = loader;
        this.projectUri = null;
        this.versionType = parseVersionType(version);
        if (this.versionType == null) {
            if (isVersionId(version)) {
                this.versionId = version;
                this.versionNumber = null;
            }
            else {
                this.versionId = null;
                this.versionNumber = version;
            }
        }
        else {
            this.versionId = null;
            this.versionNumber = null;
        }
    }

    public ProjectRef(URI projectUri, String versionId) {
        this.loader = null;
        this.projectUri = projectUri;

        final String filename = extractFilename(projectUri);
        this.idOrSlug = filename.endsWith(".mrpack") ?
            filename.substring(0, filename.length() - ".mrpack".length()) : filename;

        this.versionNumber = null;
        this.versionType = null;
        this.versionId = versionId;
    }

    private String extractFilename(URI uri) {
        final String path = uri.getPath();
        final int pos = path.lastIndexOf('/');
        return path.substring( pos >= 0 ? pos + 1 : 0);
    }

    public static ProjectRef fromPossibleUrl(
            String possibleUrl, String defaultVersion)
    {
        // First, see if it is a modrinth page URL
        try {
            final Matcher m = MODPACK_PAGE_URL.matcher(possibleUrl);
            if(m.matches()) {
                String projectSlug = m.group("slug");
                String projectVersion = m.group("versionName") != null ?
                    m.group("versionName") : defaultVersion;
                return new ProjectRef(projectSlug, projectVersion);
            } else {
                try {
                    // Might be custom URL, local file, or slug
                    // ...try as a (remote or file) URL first
                    return new ProjectRef(
                        new URL(possibleUrl).toURI(), defaultVersion
                    );
                } catch(MalformedURLException | URISyntaxException e) {
                    // Not a valid URL, so
                    // narrow down if it is a file path by looking at suffix
                    if (possibleUrl.endsWith(".mrpack")) {
                        final Path path = Paths.get(possibleUrl);
                        if (!Files.exists(path)) {
                            throw new InvalidParameterException("Given modrinth project looks like a file, but doesn't exist");
                        }

                        return new ProjectRef(
                            path.toUri(),
                            defaultVersion
                        );
                    }

                    return new ProjectRef(possibleUrl, defaultVersion);
                }
            }
        } catch (Exception e) {
            throw new InvalidParameterException("Invalid project reference: " + possibleUrl, e);
        }
    }

    public boolean hasVersionName() {
        return versionNumber != null;
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

    public boolean isFileUri() {
        return projectUri != null && projectUri.getScheme().equals("file");
    }

    private boolean isVersionId(String version) {
        return version != null && VERSIONS.matcher(version).matches();
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
