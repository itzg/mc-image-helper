package me.itzg.helpers.github.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;

/**
 * Represents the paginated response from listing artifacts for a workflow run.
 *
 * @see <a href="https://docs.github.com/en/rest/actions/artifacts#list-workflow-run-artifacts">
 *     List workflow run artifacts</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ArtifactsResponse {
    long totalCount;
    List<Artifact> artifacts;
}
