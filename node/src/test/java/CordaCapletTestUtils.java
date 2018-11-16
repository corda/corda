import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

class CordaCapletTestUtils {
    static CordaCaplet getCaplet() {
        final String path = System.getProperty("user.dir") + File.separator + "build" + File.separator + "libs" + File.separator;
        final File jar = Arrays.stream(Objects.requireNonNull(new File(path).listFiles())).filter(x -> x.getName().startsWith("corda-node") && x.getName().endsWith(".jar")).findFirst().get();
        return new CordaCaplet(new Capsule(jar.toPath()));
    }

    static String getBaseDir() {
        return Paths.get(".").toAbsolutePath().normalize().toString();
    }
}
