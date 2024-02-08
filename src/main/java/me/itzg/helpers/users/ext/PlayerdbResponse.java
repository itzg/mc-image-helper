package me.itzg.helpers.users.ext;

import lombok.Data;

@Data
public class PlayerdbResponse{
    private String code;
    private Data data;
    private boolean success;
    private String message;

    @lombok.Data
    public static class Data {
        private PlayerdbPlayer player;
    }
}
