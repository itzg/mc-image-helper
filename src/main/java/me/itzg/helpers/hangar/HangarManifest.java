package me.itzg.helpers.hangar;

import java.util.List;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@SuperBuilder
@Getter
@Jacksonized
public class HangarManifest extends BaseManifest {

    public static final String ID = "hangar";

    List<String> projects;
}
