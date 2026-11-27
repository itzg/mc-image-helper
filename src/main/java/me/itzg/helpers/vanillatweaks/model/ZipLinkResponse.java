package me.itzg.helpers.vanillatweaks.model;

import lombok.Data;

@Data
public class ZipLinkResponse {

    String status;

    String link;

    /**
     * Explains the failure when {@link #status} is "error"
     */
    String message;
}
