package me.itzg.helpers.files.archive;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class Tar extends TarArchive {

    public Tar(java.nio.file.Path archive) {
        super(archive);
    }

    @Override
    protected InputStream openInputStream() throws IOException {
        return Files.newInputStream(archive());
    }
}
