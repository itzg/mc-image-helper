package me.itzg.helpers.modrinth;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import me.itzg.helpers.modrinth.model.Project;

@RequiredArgsConstructor
@Data
class ResolvedProject {
    final private ProjectRef projectRef;
    final private Project project;
}
