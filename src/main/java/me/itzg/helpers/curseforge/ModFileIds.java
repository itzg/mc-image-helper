package me.itzg.helpers.curseforge;

import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

@Data
@Builder
@Jacksonized
public class ModFileIds {
    final int modId;
    final int fileId;
}
