package me.itzg.helpers.users.model;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data @SuperBuilder
@Jacksonized
public class JavaUser {
    final String name;

    final String uuid;
}
