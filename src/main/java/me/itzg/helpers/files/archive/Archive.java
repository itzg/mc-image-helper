package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveException;

public interface Archive {
    Path extract(Path destination, boolean overwrite) throws IOException, ArchiveException;

    boolean containsPathTraversal() throws IOException;

}