package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;

/**
 * <a href="https://docs.modrinth.com/api/operations/getproject/#200">Spec</a>
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

  List<String> gameVersions;

  List<String> loaders;
}
