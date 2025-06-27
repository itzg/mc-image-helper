package me.itzg.helpers.users.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

@Data
@SuperBuilder
@Jacksonized
public class UserDef {
    final String name;
    final List<String> flags;

    public UserDef(String input) {
        final int colonIndex = input.trim().indexOf(':');
        if (colonIndex < 0) {
            name = input.trim();
            flags = new ArrayList<>();
            return;
        }
        name = input.substring(0, colonIndex).trim();
        flags = Arrays.stream(input.substring(colonIndex + 1).split(","))
                .map(String::trim)
                .filter(flag -> !flag.isEmpty())
                .collect(Collectors.toList());
    }
}