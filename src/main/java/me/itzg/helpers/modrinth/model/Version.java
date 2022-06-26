package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
public class Version {
  String id;

  @JsonProperty("project_id")
  String projectId;

  String name;

  @JsonProperty("date_published")
  Instant datePublished;

  @JsonProperty("version_number")
  String version;

  @JsonProperty("version_type")
  VersionType versionType;

  List<VersionFile> files;

  List<VersionDependency> dependencies;

  @JsonProperty("game_versions")
  List<String> gameVersions;
}
