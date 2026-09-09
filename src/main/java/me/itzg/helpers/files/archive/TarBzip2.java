package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

public class TarBzip2 extends TarArchive {

    public TarBzip2(java.nio.file.Path archive) {
        super(archive);
    }

    @Override
    protected InputStream openInputStream() throws IOException {
        return new BZip2CompressorInputStream(Files.newInputStream(archive()));
    }
}
