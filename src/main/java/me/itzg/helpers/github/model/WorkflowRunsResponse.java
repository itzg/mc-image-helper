package me.itzg.helpers.github.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;

/**
 * Represents the response from listing runs for a GitHub Actions workflow.
 *
 * @see <a href="https://docs.github.com/en/rest/actions/workflow-runs#list-workflow-runs-for-a-workflow">
 *     List workflow runs for a workflow</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class WorkflowRunsResponse {
    List<WorkflowRun> workflowRuns;
}
