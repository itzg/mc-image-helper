package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;

/**
 * Defines how to read an archive, including any compression.
 * For example, a TAR.GZ opener wraps a gzip decompressor in a TAR reader.
 */
@FunctionalInterface
interface ArchiveOpener {
    /**
     * Creates the archive reader for the supplied stream.
     *
     * @param input stream containing the archive data
     * @return the reader used to iterate and extract entries
     * @throws IOException if the reader cannot be created
     */
    ArchiveInputStream<? extends ArchiveEntry> open(InputStream input) throws IOException;
}
