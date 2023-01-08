package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class SortableGameVersion {
	private String gameVersionPadded;
	private String gameVersion;
	private String gameVersionReleaseDate;
	private String gameVersionName;
	private int gameVersionTypeId;
}