package me.itzg.helpers.hangar.model;

import lombok.Data;

/**
 * Either {@link #downloadUrl} and {@link #fileInfo} are set for a file hosted by Hangar
 * or {@link #externalUrl} is set for a file hosted elsewhere.
 */
@Data
public class PlatformVersionDownload {
    FileInfo fileInfo;
    String downloadUrl;
    String externalUrl;
}
