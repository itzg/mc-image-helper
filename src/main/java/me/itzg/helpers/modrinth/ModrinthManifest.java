package me.itzg.helpers.modrinth;

import java.util.List;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@SuperBuilder
@Getter
@Jacksonized
public class ModrinthManifest extends BaseManifest {

    public static final String ID = "modrinth";

    List<String> projects;
}
