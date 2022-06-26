package me.itzg.helpers.modrinth;

import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Manifest {

    Instant timestamp;

    Set<String> files;
}
