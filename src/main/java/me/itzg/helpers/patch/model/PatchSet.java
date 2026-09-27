package me.itzg.helpers.patch.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(value = { "$schema" }, ignoreUnknown = false)
public class PatchSet {
    List<PatchDefinition> patches;

}
