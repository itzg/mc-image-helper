package me.itzg.helpers.users.model;

import lombok.Data;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data @Setter @SuperBuilder
@Jacksonized
public class JavaUser {
    final String name;

    String uuid;
}
