package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * <a href="https://docs.modrinth.com/api-spec/#tag/project_model">Spec</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Project {
  String slug;

  String id;

  String title;

  ProjectType projectType;

  ServerSide serverSide;

  List<String> versions;
}
