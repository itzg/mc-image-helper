package me.itzg.helpers.paper.model;

import lombok.Data;

@Data
public class ErrorResponse {
    String message;
    String error;
    boolean ok;
}
