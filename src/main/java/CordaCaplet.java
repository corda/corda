// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class CordaCaplet extends Capsule {

    protected CordaCaplet(Capsule pred) {
        super(pred);
    }

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

    // TODO: If/when capsule fix the globbing issue (See: https://github.com/puniverse/capsule/issues/109)
    //       then replace this with a simple glob
    // TODO: Make directory configurable
    private List<Path> augmentClasspath(List<Path> classpath) {
        File dir = new File("plugins");
        if(!dir.exists()) {
            dir.mkdir();
        }

        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.isFile()) {
                classpath.add(file.toPath().toAbsolutePath());
            }
        }
        return classpath;
    }
}
