package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Project {
  String slug;

  String id;

  String title;

  @JsonProperty("project_type")
  ProjectType projectType;

  @JsonProperty("server_side")
  ServerSide serverSide;
}
