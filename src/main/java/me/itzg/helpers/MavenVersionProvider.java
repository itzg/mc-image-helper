package me.itzg.helpers;

import picocli.CommandLine;

import java.io.InputStream;
import java.util.Properties;

public class MavenVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() throws Exception {
        try (InputStream in = MavenVersionProvider.class.getResourceAsStream("/META-INF/maven/com.github.itzg/mc-image-helper/pom.properties")) {
            if (in != null) {
                final Properties properties = new Properties();
                properties.load(in);

                final String version = properties.getProperty("version", "unknown");
                return new String[]{version};
            }
            else {
                return new String[]{"unknown"};
            }
        }
    }
}
