package me.itzg.helpers.paper.model;

import java.util.Map;
import lombok.Data;

@Data
public class BuildResponse {
    int id;
    Channel channel;
    Map<String, Download> downloads;
}
