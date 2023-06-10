package me.itzg.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

@SuppressWarnings("unused")
public class ServeDistributionsPlugin implements Plugin<Project> {

    public void apply(Project project) {
        project.getPlugins().withId("distribution", plugin -> {
            registerTasks(project);
        });
    }

    private void registerTasks(Project project) {
        project.getTasks().register("serveDistributions", ServeDistributionsTask.class, task -> {
            task.getBindHost().convention("0.0.0.0");
            task.getBindPort().convention(8080);
            task.setGroup("distribution");
        });
    }
}