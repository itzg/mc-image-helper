package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;

public class TarZstd extends TarArchive {

    public TarZstd(java.nio.file.Path archive) {
        super(archive);
    }

    @Override
    protected InputStream openInputStream() throws IOException {
        return new ZstdCompressorInputStream(Files.newInputStream(archive()));
    }
}
