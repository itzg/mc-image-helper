package me.itzg.helpers.modrinth.model;

public enum VersionType {
  release,
  beta,
  alpha;

  public boolean sufficientFor(VersionType levelRequired) {
    return this.ordinal() <= levelRequired.ordinal();
  }
}
