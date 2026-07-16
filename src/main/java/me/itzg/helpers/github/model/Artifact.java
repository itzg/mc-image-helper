package me.itzg.helpers.github.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import lombok.Data;
import lombok.Getter;

/**
 * Represents a GitHub Actions artifact returned for a workflow run.
 *
 * @see <a href="https://docs.github.com/en/rest/actions/artifacts#list-workflow-run-artifacts">
 *     List workflow run artifacts</a>
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Artifact {
    String name;
    String archiveDownloadUrl;
    boolean expired;
    String digest;
    Instant expiresAt;
}
