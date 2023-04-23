package me.itzg.helpers.vanillatweaks;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

import java.util.List;


@Getter
@SuperBuilder
@Jacksonized
public class VanillaTweaksManifest extends BaseManifest {
    List<String> shareCodes;
    List<String> packFiles;
}
