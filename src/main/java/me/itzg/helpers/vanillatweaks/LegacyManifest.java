package me.itzg.helpers.vanillatweaks;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder @Jacksonized
public class LegacyManifest {
    Instant timestamp;

    List<String> shareCodes;

    Set<String> files;
}
