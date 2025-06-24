package me.itzg.helpers.users.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data @SuperBuilder
@Jacksonized
public class UserDef {
    final String name;
    final List<String> flags;

    public UserDef(String input) {
        ArrayList<String> tokens = new ArrayList<String>(Arrays.asList(input.trim().split(":")));
        name = tokens.remove(0);
        flags = tokens;
    }
}