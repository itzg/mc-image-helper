package me.itzg.helpers.curseforge;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class ExcludeIncludeIds {
    Set<Integer> excludeIds;
    Set<Integer> forceIncludeIds;
}
