package me.itzg.helpers.cache;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
import picocli.CommandLine.Option;

@Data
public class CacheArgs {
    @Option(names = "--api-cache-ttl", paramLabel = "OPERATION=DURATION",
        description = "Set individual operation TTLs"
    )
    Map<String, Duration> cacheDurations;

    @Option(names = "--api-cache-default-ttl", defaultValue = "P2D", paramLabel = "DURATION",
        description = "Set default/fallback TTL in ISO-8601 duration format.\nDefault: ${DEFAULT-VALUE}"
    )
    Duration defaultCacheDuration;
}
