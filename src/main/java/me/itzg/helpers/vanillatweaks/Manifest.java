package me.itzg.helpers.vanillatweaks;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder @Jacksonized
public class Manifest {
    Instant timestamp;

    List<String> shareCodes;

    Set<String> files;
}
