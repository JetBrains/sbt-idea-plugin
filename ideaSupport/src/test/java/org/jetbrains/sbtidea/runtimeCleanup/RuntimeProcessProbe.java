package org.jetbrains.sbtidea.runtimeCleanup;

import java.nio.file.Files;
import java.nio.file.Paths;

public final class RuntimeProcessProbe {
    public static void main(String[] args) throws Exception {
        Files.write(Paths.get(args[0]), new byte[]{1});
        Thread.sleep(60000);
    }
}
