package me.itzg.helpers.patch.model;

import lombok.Data;

import java.util.List;

@Data
public class PatchSet {
    List<PatchDefinition> patches;

}
