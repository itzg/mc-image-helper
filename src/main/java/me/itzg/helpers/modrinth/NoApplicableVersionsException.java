package me.itzg.helpers.modrinth;

import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.ToString;
import me.itzg.helpers.modrinth.model.Project;
import me.itzg.helpers.modrinth.model.Version;
import me.itzg.helpers.modrinth.model.VersionType;

@Getter
@ToString
public class NoApplicableVersionsException extends RuntimeException {

    private final Project project;
    private final List<Version> versions;
    private final VersionType versionType;

    public NoApplicableVersionsException(Project project, List<Version> versions, VersionType versionType) {
        super(
            String.format("No candidate versions of '%s' [%s] matched versionType=%s",
                project.getTitle(),
                versions.stream().map(version -> version.getVersionNumber() + "=" + version.getVersionType())
                    .collect(Collectors.joining(", ")),
                versionType
            )
        );

        this.project = project;
        this.versions = versions;
        this.versionType = versionType;
    }

}
