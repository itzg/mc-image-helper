package me.itzg.helpers.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class TabularOutputTest {

    @Test
    void trimsToLimitAndSuffixes() {
        final TabularOutput tabOut = new TabularOutput('=', "  ",
            "Mod", "Version Name", "Filename", "Download page"
        )
            .limitColumnWidth(0, 20)
            .trimSuffix(1, ".jar");

        tabOut.addRow(
            "Tidal Towns", "tidal-towns-1.2.jar", "tidal-towns-1.2.jar", "https://www.curseforge.com/minecraft/mc-mods/tidal-towns/files/4723528"
        );
        tabOut.addRow(
            "Fix Experience Bug (now with FABRIC and FORGE versions)", "fabric-experiencebugfix-1.20-2.jar", "fabric-experiencebugfix-1.20-2.jar", "https://www.curseforge.com/minecraft/mc-mods/fix-experience-bug/files/4561361"
        );

        final StringWriter writer = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(writer);
        tabOut.output(printWriter);
        printWriter.flush();

        assertThat(writer.toString())
            .isEqualToNormalizingNewlines(
                "Mod                      Version Name                    Filename                            Download page                                                                \n"
                + "=======================  ==============================  ==================================  =============================================================================\n"
                + "Tidal Towns              tidal-towns-1.2                 tidal-towns-1.2.jar                 https://www.curseforge.com/minecraft/mc-mods/tidal-towns/files/4723528       \n"
                + "Fix Experience Bug (...  fabric-experiencebugfix-1.20-2  fabric-experiencebugfix-1.20-2.jar  https://www.curseforge.com/minecraft/mc-mods/fix-experience-bug/files/4561361\n");
    }
}