package me.itzg.helpers.curseforge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import me.itzg.helpers.errors.InvalidParameterException;
import org.jetbrains.annotations.NotNull;

class ModpacksPageUrlParser {
    private static final Pattern PAGE_URL_PATTERN = Pattern.compile(
        "https://(www|beta)\\.curseforge\\.com/minecraft/modpacks/(?<slug>[^/]+?)(/((files|download)(/(?<fileId>\\d+)?)?)?)?");

    private static final Pattern MINECRAFT_PAGE_URL_PATTERN = Pattern.compile(
        "https://(www|beta)\\.curseforge\\.com/minecraft/(?<category>[^/]+)/.*");

    @Data @Builder
    public static class Parsed {
        String slug;
        Integer fileId;
    }

    @NotNull
    public static Parsed parse(String pageUrl) {
        if (pageUrl == null) {
            return Parsed.builder().build();
        }

        final Matcher m = PAGE_URL_PATTERN.matcher(pageUrl);
        if (m.matches()) {
            final String slug = m.group("slug");
            final String fileIdStr = m.group("fileId");
            if (fileIdStr != null) {
                return Parsed.builder()
                    .slug(slug)
                    .fileId(Integer.parseInt(fileIdStr))
                    .build();
            }
            else {
                return Parsed.builder()
                    .slug(slug)
                    .build();
            }
        }

        final Matcher other = MINECRAFT_PAGE_URL_PATTERN.matcher(pageUrl);
        if (other.matches()) {
            final String category = other.group("category");
            if (!CurseForgeApiClient.CATEGORY_MODPACKS.equals(category)) {
                String message = "install-curseforge expects a modpack page URL such as "
                    + "https://www.curseforge.com/minecraft/modpacks/<slug>, not a "
                    + category + " page: " + pageUrl;
                if (CurseForgeApiClient.CATEGORY_MC_MODS.equals(category)
                    || CurseForgeApiClient.CATEGORY_BUKKIT_PLUGINS.equals(category)) {
                    message += ". Use curseforge-files for individual mods or plugins.";
                }
                throw new InvalidParameterException(message);
            }
        }

        throw new InvalidParameterException("Unexpected CF page URL structure: " + pageUrl);
    }
}
