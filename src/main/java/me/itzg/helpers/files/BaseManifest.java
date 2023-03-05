package me.itzg.helpers.files;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import lombok.Builder.Default;
import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Sub-classes should be declared with:
 * <pre>
 * {@code
 * @Getter
 * @SuperBuilder
 * @Jacksonized
 * }
 * </pre>
 */
@Data
@SuperBuilder
@JsonTypeInfo(use = Id.CLASS, property = "@type")
public abstract class BaseManifest {
    @Default
    Instant timestamp = Instant.now();

    /**
     * NOTE: use {@link Manifests#relativizeAll(Path, List)} to remap regular paths into relative paths
     */
    List<String> files;
}
