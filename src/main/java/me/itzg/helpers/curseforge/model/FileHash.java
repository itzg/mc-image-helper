package me.itzg.helpers.curseforge.model;

import lombok.Data;

@Data
public class FileHash {
	private String value;
	private HashAlgo algo;
}