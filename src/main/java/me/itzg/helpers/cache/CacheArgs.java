package me.itzg.helpers.cache;

import java.time.Duration;
import java.util.Map;
import lombok.Data;
import picocli.CommandLine.Option;

@Data
public class CacheArgs {
    @Option(names = "--api-cache-ttl")
    Map<String, Duration> cacheDurations;

    @Option(names = "--api-cache-default-ttl", defaultValue = "P2D")
    Duration defaultCacheDuration;
}
