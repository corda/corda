// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import sun.misc.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

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
        // Equality is used here because Capsule never instantiates these attributes but instead reuses the ones
        // defined as public static final fields on the Capsule class, therefore referential equality is safe.
        if (ATTR_APP_CLASS_PATH == attr) {
            T cp = super.attribute(attr);

            (new File("cordapps")).mkdir();
            augmentClasspath((List<Path>) cp, "cordapps");
            augmentClasspath((List<Path>) cp, "plugins");
            return cp;
        }
        return super.attribute(attr);
    }

    // TODO: Make directory configurable via the capsule manifest.
    // TODO: Add working directory variable to capsules string replacement variables.
    private void augmentClasspath(List<Path> classpath, String dirName) {
        File dir = new File(dirName);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            for (File file : files) {
                if (file.isFile() && isJAR(file)) {
                    classpath.add(file.toPath().toAbsolutePath());
                }
            }
        }
    }

    @Override
    protected void liftoff() {
        super.liftoff();
        Signal.handle(new Signal("INT"), new SignalHandler() {
            @Override
            public void handle(Signal signal) {
                // Disable Ctrl-C for this process, so the child process can handle it in the shell instead.
            }
        });
    }

    private Boolean isJAR(File file) {
        return file.getName().toLowerCase().endsWith(".jar");
    }
}
