package me.itzg.helpers.curseforge;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static me.itzg.helpers.curseforge.ModFileRefResolver.idsFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import me.itzg.helpers.cache.ApiCachingDisabled;
import me.itzg.helpers.curseforge.model.CurseForgeFile;
import me.itzg.helpers.curseforge.model.ModLoaderType;
import me.itzg.helpers.http.SharedFetch.Options;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ModFileRefResolverTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig()
            .dynamicPort()
            .usingFilesUnderClasspath("curseforge")
        )
        .configureStaticDsl(true)
        .build();

    public static Stream<Arguments> testMcModsRefsArgs() {
        return Stream.of(
            arguments("238222:4644453", null, null, null, new ModFileIds(238222, 4644453)),
            arguments("jei:4644453", CurseForgeApiClient.CATEGORY_MC_MODS, null, null,
                new ModFileIds(238222, 4644453)
            ),
            arguments("jei@11.6.0.1016", CurseForgeApiClient.CATEGORY_MC_MODS, "1.19.2", ModLoaderType.Forge,
                new ModFileIds(238222, 4615177)
            ),
            arguments("https://www.curseforge.com/minecraft/mc-mods/jei/files/4644453", null, null, null,
                new ModFileIds(238222, 4644453)
            ),
            arguments("https://www.curseforge.com/minecraft/mc-mods/jei", null, "1.20.1", ModLoaderType.Fabric,
                new ModFileIds(238222, 4644452)
            )
        );
    }

    @ParameterizedTest
    @MethodSource("testMcModsRefsArgs")
    void testMcModsRefs(String ref, String defaultCategory, String gameVersion, ModLoaderType modLoader, ModFileIds expected) {

        final CurseForgeApiClient apiClient = new CurseForgeApiClient(wm.baseUrl(), "testing", Options.builder().build(),
            CurseForgeApiClient.MINECRAFT_GAME_ID, new ApiCachingDisabled()
        );

        final CategoryInfo categoryInfo = apiClient.loadCategoryInfo(
                Arrays.asList(CurseForgeApiClient.CATEGORY_MC_MODS, CurseForgeApiClient.CATEGORY_BUKKIT_PLUGINS))
            .block();

        final ModFileRefResolver resolver = new ModFileRefResolver(apiClient, categoryInfo);
        final List<CurseForgeFile> results = resolver.resolveModFiles(Collections.singletonList(ref), defaultCategory, gameVersion,
                modLoader
            )
            .block();

        assertThat(results).hasSize(1);
        assertThat(idsFrom(results.get(0))).isEqualTo(expected);
    }
}