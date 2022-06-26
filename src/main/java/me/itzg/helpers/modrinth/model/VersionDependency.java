package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class VersionDependency {
  @JsonProperty("version_id")
  String versionId;

  @JsonProperty("project_id")
  String projectId;

  @JsonProperty("dependency_type")
  DependencyType dependencyType;
}
