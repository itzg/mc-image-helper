package me.itzg.helpers.modrinth.model;

import java.util.Map;
import lombok.Data;

/**
 * Refer to <code>files</code> of <a href="https://docs.modrinth.com/api/operations/getversion/#200">getversion</a>
 */
@Data
public class VersionFile {

    /**
     * key is either sha512 or sha1
     */
    Map<String, String> hashes;

    String url;

    String filename;

    boolean primary;
}
