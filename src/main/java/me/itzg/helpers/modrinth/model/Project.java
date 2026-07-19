package me.itzg.helpers.modrinth.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;
import lombok.ToString;

/**
 * <a href="https://docs.modrinth.com/api/operations/getproject/#200">Spec</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@ToString(onlyExplicitlyIncluded = true)
public class Project {

    @ToString.Include
    String slug;

    @ToString.Include
    String id;

    @ToString.Include
    String title;

    @ToString.Include
    ProjectType projectType;

    @ToString.Include
    ServerSide serverSide;

    List<String> versions;

    List<String> gameVersions;

    List<String> loaders;
}
