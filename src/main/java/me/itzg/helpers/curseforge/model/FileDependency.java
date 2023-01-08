package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class FileDependency {
	private FileRelationType relationType;
	private int modId;
}