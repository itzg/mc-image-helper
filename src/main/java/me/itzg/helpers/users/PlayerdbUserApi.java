package me.itzg.helpers.users;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.users.ext.PlayerdbResponse;
import me.itzg.helpers.users.model.JavaUser;
import reactor.core.publisher.Mono;

@Slf4j
public class PlayerdbUserApi implements UserApi {
    private final SharedFetch sharedFetch;
    private final UriBuilder urlBuilder;

    public PlayerdbUserApi(SharedFetch sharedFetch, String apiBaseUrl) {
        this.sharedFetch = sharedFetch;
        this.urlBuilder = UriBuilder.withBaseUrl(apiBaseUrl);
    }

    @Override
    public JavaUser resolveUser(String input) {
        log.debug("Resolving user={} from PlayerDB API", input);
        return sharedFetch.fetch(
            urlBuilder.resolve("/api/player/minecraft/{input}", input)
        )
            .toObject(PlayerdbResponse.class)
            .assemble()
            .onErrorMap(FailedRequestException.class, e -> {
                if (e.getStatusCode() == 400 || e.getStatusCode() == 404) {
                    return new InvalidParameterException("Could not resolve user from Playerdb: " + input);
                }
                return e;
            })
            .flatMap(resp -> {
                if (resp.isSuccess()) {
                    return Mono.just(
                        JavaUser.builder()
                            .name(resp.getData().getPlayer().getUsername())
                            .uuid(resp.getData().getPlayer().getId())
                            .build()
                    );
                }
                else {
                    return Mono.error(
                        new InvalidParameterException("Could not resolve user from Playerdb: " + input)
                    );
                }
            })
            .block();
    }
}
