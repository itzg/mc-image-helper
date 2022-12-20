package me.itzg.helpers.files;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.time.Instant;
import java.util.List;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@JsonTypeInfo(use = Id.CLASS, property = "@type")
public abstract class BaseManifest {
    @Default
    Instant timestamp = Instant.now();

    List<String> files;
}
