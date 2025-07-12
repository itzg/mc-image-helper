package me.itzg.helpers.paper.model;

import java.util.List;
import lombok.Data;

@Data
public class VersionResponse {
    Version version;
    List<Integer> builds;
}
