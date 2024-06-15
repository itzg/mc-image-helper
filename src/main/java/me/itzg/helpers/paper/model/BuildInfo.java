package me.itzg.helpers.paper.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BuildInfo {
    int build;
    ReleaseChannel channel;
    Map<String, DownloadInfo> downloads;

    @Data
    public static class DownloadInfo {
        String name;
    }
}
