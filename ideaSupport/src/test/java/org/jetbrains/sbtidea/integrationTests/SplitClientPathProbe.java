package org.jetbrains.sbtidea.integrationTests;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/** Executes the SDK's property loader in a fresh JVM, without booting a graphical client. */
public final class SplitClientPathProbe {
    public static void main(String[] args) throws Exception {
        String[] keys = {"idea.system.path", "idea.config.path", "idea.log.path", "plugin.path"};
        for (String key : keys) {
            if (System.getProperty(key) != null) {
                throw new AssertionError("Expected the generated properties file to supply " + key);
            }
        }
        Class<?> pathManager = Class.forName("com.intellij.openapi.application.PathManager");
        pathManager.getMethod("loadProperties").invoke(null);
        Properties observed = new Properties();
        for (String key : keys) {
            observed.setProperty(key, System.getProperty(key));
        }
        observed.setProperty("resolved.system", (String) pathManager.getMethod("getSystemPath").invoke(null));
        observed.setProperty("resolved.config", (String) pathManager.getMethod("getConfigPath").invoke(null));
        observed.setProperty("resolved.log", (String) pathManager.getMethod("getLogPath").invoke(null));
        try (OutputStream out = Files.newOutputStream(Paths.get(args[0]))) {
            observed.store(out, "Frontend paths loaded by the real IntelliJ SDK");
        }
    }
}
