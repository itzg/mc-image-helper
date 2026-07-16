package me.itzg.helpers.github.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

/**
 * Represents a GitHub Actions workflow run.
 *
 * @see <a href="https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow">
 *     List workflow runs for a workflow</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorkflowRun {
    long id;
}
