package org.jetbrains.sbtidea.integrationTests;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Exercises the SDK's real property loader with a production profile confined to a temporary home. */
public final class RuntimePropertiesProbe {
    public static void main(String[] args) throws Exception {
        Class<?> pathManager = Class.forName("com.intellij.openapi.application.PathManager");
        Path productionConfig = Paths.get((String) pathManager
                .getMethod("getDefaultConfigPathFor", String.class).invoke(null, args[1]));
        if (!productionConfig.startsWith(Paths.get(System.getProperty("user.home")))) {
            throw new AssertionError("Production fixture escaped temporary home: " + productionConfig);
        }
        Files.createDirectories(productionConfig);
        Files.write(productionConfig.resolve("idea.properties"),
                "scl25595.production=leaked\n".getBytes(StandardCharsets.UTF_8));

        pathManager.getMethod("loadProperties").invoke(null);
        Properties observed = new Properties();
        for (String key : new String[]{"scl25595.production", "scl25595.runtime", "scl25595.custom",
                "idea.paths.selector", "idea.properties.file"}) {
            observed.setProperty(key, System.getProperty(key, "<absent>"));
        }
        observed.setProperty("resolved.config", (String) pathManager.getMethod("getConfigPath").invoke(null));
        try (OutputStream out = Files.newOutputStream(Paths.get(args[0]))) {
            observed.store(out, "Properties loaded by the IntelliJ SDK");
        }
    }
}
