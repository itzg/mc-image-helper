package me.itzg.helpers.paper.model;

import lombok.Data;

@Data
public class Version {
    String id;
    Support support;
    JavaInfo java;
}
