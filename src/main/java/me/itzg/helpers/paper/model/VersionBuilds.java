package me.itzg.helpers.paper.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;

/**
 * Response from <a href="https://api.papermc.io/docs/swagger-ui/index.html?configUrl=/openapi/swagger-config#/version-builds-controller/builds">version-builds-controller</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VersionBuilds {
    String version;
    List<BuildInfo> builds;
}
