package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.ToString;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@ToString(onlyExplicitlyIncluded = true)
public class Version {

    @ToString.Include
    private String id;

    @ToString.Include
    private String projectId;

    @ToString.Include
    private String name;

    private Instant datePublished;

    private String versionNumber;

    @ToString.Include
    private VersionType versionType;

    private List<VersionFile> files;

    private List<VersionDependency> dependencies;

    private List<String> gameVersions;

    private List<String> loaders;
}
