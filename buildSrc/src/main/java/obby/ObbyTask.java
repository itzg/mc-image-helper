package obby;

import java.io.File;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@SuppressWarnings("unused")
public abstract class ObbyTask extends DefaultTask {

    @Input
    public abstract MapProperty<String, String> getClearTextProperties();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void perform() throws IOException {
        Obby obby = new Obby(getClearTextProperties().get());
        final File out = getOutputFile().getAsFile().get();
        obby.writeToFile(out.toPath());
        getLogger().info("ObbyTask: Wrote encrypted properties to " + out.getAbsolutePath());
    }


}

