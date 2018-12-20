import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CordaWebServerCaplet extends CordaCaplet {
    protected CordaWebServerCaplet(Capsule pred) {
        super(pred);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Map.Entry<String, T> attr) {
        T cp = super.attribute(attr);
        if (ATTR_APP_CLASS_PATH == attr) {
            File cordappsDir = new File(baseDir, "cordapps");
            // Create cordapps directory if it doesn't exist.
            if (checkIfCordappDirExists(cordappsDir)) {
                // Add additional directories of JARs to the classpath (at the end), e.g., for JDBC drivers.
                augmentClasspath((List<Path>) cp, cordappsDir);
            }
        }
        return cp;
    }
}
