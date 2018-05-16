package net.corda.plugins;

import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class CopyUtils {

    private CopyUtils() {
    }

    public static long installResource(TemporaryFolder folder, String resourceName) throws IOException {
        File buildFile = folder.newFile(resourceName.substring(1 + resourceName.lastIndexOf('/')));
        return copyResourceTo(resourceName, buildFile);
    }

    public static long copyResourceTo(String resourceName, Path target) throws IOException {
        try (InputStream input = CopyUtils.class.getClassLoader().getResourceAsStream(resourceName)) {
            return Files.copy(input, target, REPLACE_EXISTING);
        }
    }

    public static long copyResourceTo(String resourceName, File target) throws IOException {
        return copyResourceTo(resourceName, target.toPath());
    }
}
