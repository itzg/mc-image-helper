package me.itzg.helpers.paper.model;

import java.util.List;
import lombok.Data;

@Data
public class JavaInfo {
    JavaVersion version;
    JavaFlags flags;

    @Data
    public static class JavaVersion {
        int minimum;
    }

    @Data
    public static class JavaFlags {
        List<String> recommended;
    }
}
