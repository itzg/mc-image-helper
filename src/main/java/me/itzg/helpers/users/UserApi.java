package me.itzg.helpers.users;

import me.itzg.helpers.users.model.JavaUser;

public interface UserApi {

    JavaUser resolveUser(String input);
}
