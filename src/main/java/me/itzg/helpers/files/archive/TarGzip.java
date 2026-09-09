package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class TarGzip extends TarArchive {

    public TarGzip(java.nio.file.Path archive) {
        super(archive);
    }

    @Override
    protected InputStream openInputStream() throws IOException {
        return new GzipCompressorInputStream(Files.newInputStream(archive()));
    }
}
