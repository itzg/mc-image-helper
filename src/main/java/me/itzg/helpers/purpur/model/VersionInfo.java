package me.itzg.helpers.purpur.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class VersionInfo {
    private Builds builds;

    @Data
    public static class Builds {
        private String latest;
        private List<String> all;
    }
}
