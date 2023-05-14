package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Version {
  String id;

  String projectId;

  String name;

  Instant datePublished;

  String version;

  VersionType versionType;

  List<VersionFile> files;

  List<VersionDependency> dependencies;

  List<String> gameVersions;
}
