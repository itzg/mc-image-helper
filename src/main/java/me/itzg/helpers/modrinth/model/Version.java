package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Version {

    private String id;

    private String projectId;

    private String name;

    private Instant datePublished;

    private String versionNumber;

    private VersionType versionType;

    private List<VersionFile> files;

    private List<VersionDependency> dependencies;

    private List<String> gameVersions;

    private List<String> loaders;
}
