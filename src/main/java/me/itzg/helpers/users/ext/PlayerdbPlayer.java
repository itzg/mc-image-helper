package me.itzg.helpers.users.ext;

import lombok.Data;

@Data
public class PlayerdbPlayer {
    /**
     * Mojang raw ID, which is a UUID without dashes
     */
    private String rawId;
    private String id;
    private String username;
}