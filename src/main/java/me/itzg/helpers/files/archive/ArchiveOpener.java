package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@FunctionalInterface
interface ArchiveOpener {
    InputStream open(Path path) throws IOException;
}
