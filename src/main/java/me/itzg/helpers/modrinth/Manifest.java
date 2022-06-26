package me.itzg.helpers.modrinth;

import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class Manifest {

    Set<String> files;
}
