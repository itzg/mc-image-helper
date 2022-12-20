package me.itzg.helpers.fabric;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import me.itzg.helpers.files.BaseManifest;

@Getter
@SuperBuilder
@Jacksonized
public class FabricManifest extends BaseManifest {

    Origin origin;

    @JsonTypeInfo(use = Id.NAME)
    @JsonSubTypes({
        @Type(value = Versions.class, name = "versions"),
        @Type(value = LocalFile.class, name = "file"),
        @Type(value = RemoteFile.class, name = "remote")
    })
    public static abstract class Origin {

    }

    @Getter
    @Builder
    @Jacksonized
    @EqualsAndHashCode(callSuper = false)
    @ToString
    public static class Versions extends Origin {

        String gameVersion;
        String loaderVersion;
        String installerVersion;

    }

    @Getter
    @Builder
    @Jacksonized
    @EqualsAndHashCode(callSuper = false)
    @ToString
    public static class LocalFile extends Origin {

        String checksum;
    }

    @Getter
    @Builder
    @Jacksonized
    @EqualsAndHashCode(callSuper = false)
    @ToString
    public static class RemoteFile extends Origin {

        String uri;
    }
}
