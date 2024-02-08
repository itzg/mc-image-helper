package me.itzg.helpers.users;

import lombok.extern.slf4j.Slf4j;
import me.itzg.helpers.errors.GenericException;
import me.itzg.helpers.errors.InvalidParameterException;
import me.itzg.helpers.http.FailedRequestException;
import me.itzg.helpers.http.SharedFetch;
import me.itzg.helpers.http.UriBuilder;
import me.itzg.helpers.users.ext.MojangProfile;
import me.itzg.helpers.users.model.JavaUser;

@Slf4j
public class MojangUserApi implements UserApi {
    private final SharedFetch sharedFetch;
    private final UriBuilder uriBuilder;

    public MojangUserApi(SharedFetch sharedFetch, String apiBaseUrl) {
        this.sharedFetch = sharedFetch;
        this.uriBuilder = UriBuilder.withBaseUrl(apiBaseUrl);
    }

    @Override
    public JavaUser resolveUser(String input) {
        log.debug("Resolving user={} from Mojang API", input);
        final MojangProfile profile = sharedFetch.fetch(
                uriBuilder.resolve("/users/profiles/minecraft/{username}", input)
            )
            .toObject(MojangProfile.class)
            .assemble()
            .onErrorMap(e -> {
                if (e instanceof FailedRequestException) {
                    if (((FailedRequestException) e).getStatusCode() == 404) {
                        return new InvalidParameterException("Could not resolve username from Mojang: " + input);
                    }
                }
                return e;
            })
            .block();

        if (profile == null) {
            throw new GenericException("Profile was not available from Mojang for " + input);
        }

        log.debug("Resolved '{}' from Mojang profile lookup: {}", input, profile);
        return JavaUser.builder()
            .name(profile.getName())
            .uuid(UuidQuirks.addDashesToId(profile.getId()))
            .build();
    }

}
