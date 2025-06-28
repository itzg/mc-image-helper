package me.itzg.helpers.paper.model;

import lombok.Data;

@Data
public class Download {

    Checksums checksums;
    String name;
    String url;
}
