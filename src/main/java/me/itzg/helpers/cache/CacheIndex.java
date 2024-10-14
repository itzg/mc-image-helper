package me.itzg.helpers.cache;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class CacheIndex {
    private Map<String/*operation*/, Map<String/*keys*/, CacheEntry>> operations = new HashMap<>();

    @Data
    public static class CacheEntry {
        private String filename;
        private Instant expiresAt;
    }
}
