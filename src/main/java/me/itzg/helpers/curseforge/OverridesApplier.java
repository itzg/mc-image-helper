package me.itzg.helpers.curseforge;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.AllArgsConstructor;

interface OverridesApplier {

    Result apply() throws IOException;

    @AllArgsConstructor
    class Result {

        List<Path> paths;
        String levelName;
    }
}
