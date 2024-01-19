package org.jetbrains.sbtidea.download;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public class NioUtils {
    public static void delete(Path path) throws IOException {
        if (!path.toFile().exists()) return;

        try(Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(NioUtils::silentDelete);
        }
    }

    private static void silentDelete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException ignored) { }
    }
}
