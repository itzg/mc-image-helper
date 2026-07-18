package me.itzg.helpers.versions;

import me.itzg.helpers.files.ChecksumAlgo;
import java.net.URI;

public record MinecraftJarInfo(URI url, int size, ChecksumAlgo checksumAlgo, String checksum) { }
