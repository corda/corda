import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ExplorerCaplet extends Capsule {

    @SuppressWarnings("unused")
    protected ExplorerCaplet(Capsule pred) {
        super(pred);
    }

    /**
     * Overriding the Caplet classpath generation via the intended interface in Capsule.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Map.Entry<String, T> attr) {
        // Equality is used here because Capsule never instantiates these attributes but instead reuses the ones
        // defined as public static final fields on the Capsule class, therefore referential equality is safe.
        if (ATTR_APP_CLASS_PATH == attr) {
            T cp = super.attribute(attr);
            List<Path> classpath = augmentClasspath((List<Path>) cp, "cordapps");
            return (T) augmentClasspath(classpath, "dependencies");
        }
        return super.attribute(attr);
    }

    // TODO: Make directory configurable via the capsule manifest.
    // TODO: Add working directory variable to capsules string replacement variables.
    private List<Path> augmentClasspath(List<Path> classpath, String dirName) {
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdir();
        }

        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isFile() && isJAR(file)) {
                classpath.add(file.toPath().toAbsolutePath());
            }
        }
        return classpath;
    }

    private Boolean isJAR(File file) {
        return file.getName().toLowerCase().endsWith(".jar");
    }

}
