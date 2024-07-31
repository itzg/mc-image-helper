package me.itzg.helpers.curseforge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.curseforge.model.FileHash;
import me.itzg.helpers.curseforge.model.HashAlgo;
import me.itzg.helpers.files.ChecksumAlgo;
import me.itzg.helpers.files.Checksums;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
public class FileHashVerifier {

    private final static Map<HashAlgo, ChecksumAlgo> algos = new EnumMap<>(HashAlgo.class);

    static {
        algos.put(HashAlgo.Md5, ChecksumAlgo.MD5);
        algos.put(HashAlgo.Sha1, ChecksumAlgo.SHA1);
    }

    /**
     * @return Mono.error with {@link FileHashInvalidException}
     * or {@link IllegalArgumentException} when compatible checksum algorithm can't be found
     */
    public static Mono<Path> verify(Path file, List<FileHash> hashes) {
        if (hashes.isEmpty()) {
            return Mono.just(file);
        }

        for (final FileHash hash : hashes) {
            final ChecksumAlgo checksumAlgo = algos.get(hash.getAlgo());
            if (checksumAlgo != null) {
                return Mono.fromCallable(() -> {
                        log.debug("Verifying hash of {}", file);

                        if (!Checksums.valid(file, checksumAlgo, hash.getValue())) {
                            Files.delete(file);
                            throw new FileHashInvalidException("Incorrect checksum: " + file);
                        }
                        else {
                            return file;
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic());
            }
        }

        return Mono.error(new IllegalArgumentException("Unable to find compatible checksum algorithm from " +
            hashes.stream()
                .map(FileHash::getAlgo)
                .collect(Collectors.toList())
            ));
    }
}
