package net.corda.plugins;

import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.*;

public final class CopyUtils {
    private static final String testGradleUserHome = System.getProperty("test.gradle.user.home", "");

    private CopyUtils() {
    }

    public static long copyResourceTo(String resourceName, Path target) throws IOException {
        try (InputStream input = CopyUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            return Files.copy(input, target, REPLACE_EXISTING);
        }
    }

    public static long copyResourceTo(String resourceName, File target) throws IOException {
        return copyResourceTo(resourceName, target.toPath());
    }

    public static String toString(Path file) throws IOException {
        return new String(Files.readAllBytes(file), UTF_8);
    }

    public static Path pathOf(TemporaryFolder folder, String... elements) {
        return Paths.get(folder.getRoot().getAbsolutePath(), elements);
    }

    public static List<String> getGradleArgsForTasks(String... taskNames) {
        List<String> args = new ArrayList<>(taskNames.length + 3);
        Collections.addAll(args, taskNames);
        args.add("--info");
        if (!testGradleUserHome.isEmpty()) {
            Collections.addAll(args,"-g", testGradleUserHome);
        }
        return args;
    }
}
