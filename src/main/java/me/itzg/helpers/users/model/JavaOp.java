package me.itzg.helpers.users.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Jacksonized
public class JavaOp extends JavaUser {

    final int level;

    final boolean bypassesPlayerLimit;
}
