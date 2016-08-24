// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CordaCaplet extends Capsule {

    protected CordaCaplet(Capsule pred) {
        super(pred);
    }

    /**
     * Overriding the Caplet classpath generation via the intended interface in Capsule.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Map.Entry<String, T> attr) {
        if(ATTR_APP_CLASS_PATH == attr) {
            T cp = super.attribute(attr);
            List<Path> classpath = (List<Path>) cp;
            return (T) augmentClasspath(classpath);
        }
        return super.attribute(attr);
    }

    // TODO: Make directory configurable via the capsule manifest.
    // TODO: Add working directory variable to capsules string replacement variables.
    private List<Path> augmentClasspath(List<Path> classpath) {
        File dir = new File("plugins");
        if(!dir.exists()) {
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
        String[] parts = file.getName().split("\\.");
        return (parts.length > 1) && (parts[parts.length - 1].toLowerCase().equals("jar"));
    }
}
