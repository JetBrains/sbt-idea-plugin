import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public class DirectoryProbe {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        for (String key : new String[]{"idea.system.path", "idea.config.path", "idea.log.path",
                "idea.plugins.path", "idea.paths.selector", "idea.properties.file", "scope.marker", "product.marker"}) {
            properties.setProperty(key, System.getProperty(key, "<absent>"));
        }
        Path system = Paths.get(properties.getProperty("idea.system.path"));
        Files.createDirectories(system);
        Files.createDirectories(Paths.get(properties.getProperty("idea.config.path")));
        Files.createDirectories(Paths.get(properties.getProperty("idea.log.path")));
        // A shared system directory causes the second probe to fail while the first holds this lock.
        try (FileChannel channel = FileChannel.open(system.resolve("probe.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new AssertionError("Test system directory is already in use");
            Path output = Paths.get(args[0]);
            try (OutputStream out = Files.newOutputStream(output)) {
                properties.store(out, "Actual JVM system properties");
            }
            if (args.length > 1 && !args[1].isEmpty()) {
                Path peer = Paths.get(args[1]);
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
                while (!Files.exists(peer)) {
                    if (System.nanoTime() > deadline) throw new AssertionError("Concurrent peer did not start");
                    Thread.sleep(50);
                }
            }
        }
    }
}
