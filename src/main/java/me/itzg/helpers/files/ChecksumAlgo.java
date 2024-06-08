package me.itzg.helpers.files;

import lombok.Getter;

@Getter
public enum ChecksumAlgo {
    MD5("md5", "MD5"),
    SHA1("sha1", "SHA-1"),
    SHA256("sha256", "SHA-256"),;

    private final String prefix;

    private final String jdkAlgo;

    ChecksumAlgo(String prefix, String jdkAlgo) {
        this.prefix = prefix;
        this.jdkAlgo = jdkAlgo;
    }
}
