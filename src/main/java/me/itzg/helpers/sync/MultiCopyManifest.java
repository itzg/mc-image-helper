package me.itzg.helpers.sync;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@SuperBuilder
@Getter
@Jacksonized
public class MultiCopyManifest extends BaseManifest {

}
