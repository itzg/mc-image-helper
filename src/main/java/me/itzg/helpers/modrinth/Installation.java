package me.itzg.helpers.modrinth;

import java.nio.file.Path;
import java.util.List;
import lombok.Data;
import me.itzg.helpers.modrinth.model.ModpackIndex;

@Data
public class Installation {

    ModpackIndex index;
    List<Path> files;
}
