package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class CurseForgeResponse<T> {
    private boolean ok;
    private String error;
    private T data;
}
