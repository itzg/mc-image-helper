package me.itzg.helpers.curseforge.model;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.List;
import lombok.Data;

@Data
@JsonTypeName("File")
public class CurseForgeFile {
	private int gameId;
	private boolean isAvailable;
	private String fileName;
	private List<String> gameVersions;
	private String displayName;
	private List<SortableGameVersion> sortableGameVersions;
	private String downloadUrl;
	private Instant fileDate;
	private Boolean exposeAsAlternative;
	private int modId;
	private List<FileModule> modules;
	private List<FileDependency> dependencies;
	private long fileFingerprint;
	private FileStatus fileStatus;
	private boolean isServerPack;
	private FileReleaseType releaseType;
	private List<FileHash> hashes;
	private Integer parentProjectFileId;
	private Integer alternateFileId;
	private int id;
	private long fileLength;
	private long downloadCount;
	private Integer serverPackFileId;
}