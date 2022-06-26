package me.itzg.helpers.modrinth.model;

import java.util.Map;
import lombok.Data;

@Data
public class VersionFile {
  Map<String,String> hashes;

  String url;

  String filename;

  boolean primary;
}
