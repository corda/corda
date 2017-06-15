import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AbstractCordaCaplet extends Capsule {
    protected AbstractCordaCaplet(Capsule pred) {
        super(pred);
    }

    /**
     * Overriding the Caplet classpath generation via the intended interface in Capsule.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected <T> T attribute(Map.Entry<String, T> attr) {
        T value = super.attribute(attr);
        // Equality is used here because Capsule never instantiates these attributes but instead reuses the ones
        // defined as public static final fields on the Capsule class, therefore referential equality is safe.
        if (ATTR_APP_CLASS_PATH == attr) {
            // TODO: Make directory configurable via the capsule manifest.
            // TODO: Add working directory variable to capsules string replacement variables.
            File pluginsPath = new File("plugins");
            pluginsPath.mkdir(); // XXX: Can't we do this somewhere else?
            ((List<Path>) value).add(pluginsPath.toPath().toAbsolutePath().resolve("*")); // Literal asterisk.
        }
        return value;
    }
}
