package me.itzg.helpers.fabric;

import lombok.Data;

@Data
public class InstallerEntry{
    private String maven;
    private boolean stable;
    private String version;
    private String url;
}