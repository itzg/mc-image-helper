package me.itzg.helpers.modrinth;

import lombok.Data;
import me.itzg.helpers.modrinth.model.VersionType;
import picocli.CommandLine;

@Data
public class VersionTypePref {

    public static final String OPTION_DESCRIPTION = "Valid values: release, beta, alpha (optional '+' suffix)";
    private final VersionType versionType;
    private final boolean prefersBest;

    private VersionTypePref(VersionType versionType, boolean prefersBest) {
        this.versionType = versionType;
        this.prefersBest = prefersBest;
    }

    public static VersionTypePref parse(String input) {
        if (input.endsWith("+")) {
            return new VersionTypePref(VersionType.valueOf(input.substring(0, input.length() - 1).toLowerCase()), true);
        }
        else {
            return new VersionTypePref(VersionType.valueOf(input.toLowerCase()), false);
        }
    }

    public static VersionTypePref of(VersionType versionType) {
        return new VersionTypePref(versionType, false);
    }

    public static VersionTypePref of(VersionType versionType, boolean prefersBest) {
        if (versionType == null) {
            return null;
        }
        return new VersionTypePref(versionType, prefersBest);
    }

    public static class Converter implements CommandLine.ITypeConverter<VersionTypePref> {
        @Override
        public VersionTypePref convert(String value) throws Exception {
            return VersionTypePref.parse(value);
        }
    }
}
