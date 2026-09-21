package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

@FunctionalInterface
interface ArchiveOpener {
    ArchiveInputStream<? extends ArchiveEntry> open(InputStream input) throws IOException;
}