package me.itzg.helpers.files;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OsUtils {

    public boolean notWindows() {
        return !System.getProperty("os.name").toLowerCase().contains("win");
    }
}
