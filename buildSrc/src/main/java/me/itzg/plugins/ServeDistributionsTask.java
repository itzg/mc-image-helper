package me.itzg.plugins;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import com.sun.net.httpserver.SimpleFileServer.OutputLevel;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class ServeDistributionsTask extends DefaultTask {

    @Input
    abstract Property<String> getBindHost();

    @Input
    abstract Property<Integer> getBindPort();

    @TaskAction
    void serve() {
        final Path distributionsPath = getProject().getLayout().getBuildDirectory().dir("distributions").get().getAsFile().toPath();

        final InetSocketAddress binding = new InetSocketAddress(getBindHost().get(), getBindPort().get());

        final HttpServer server = SimpleFileServer.createFileServer(binding,
            distributionsPath,
            OutputLevel.INFO
        );

        getLogger().quiet("Starting file serving of {} at {}:{}",
            distributionsPath, getBindHost().get(), getBindPort().get()
        );
        server.start();

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            server.stop(10);
        }
    }
}
