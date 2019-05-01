import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BridgeCaplet extends Capsule {
    private String baseDir = null;

    protected BridgeCaplet(Capsule pred) {
        super(pred);
    }

    @Override
    protected ProcessBuilder prelaunch(List<String> jvmArgs, List<String> args) {
        this.baseDir = getBaseDirectory(args);
        return super.prelaunch(jvmArgs, args);
    }

    private String getBaseDirectory(List<String> args) {
        String baseDir = getOptionMultiple(args, Arrays.asList("--base-directory", "-b"));
        return Paths.get((baseDir == null) ? "." : baseDir).toAbsolutePath().normalize().toString();
    }

    private String getOptionMultiple(List<String> args, List<String> possibleOptions) {
        String result = null;
        for (String option : possibleOptions) {
            result = getOption(args, option);
            if (result != null) break;
        }
        return result;
    }

    private String getOption(List<String> args, String option) {
        final String lowerCaseOption = option.toLowerCase();
        int index = 0;
        for (String arg : args) {
            if (arg.toLowerCase().equals(lowerCaseOption)) {
                if (index < args.size() - 1 && !args.get(index + 1).startsWith("-")) {
                    return args.get(index + 1);
                } else {
                    return null;
                }
            }

            if (arg.toLowerCase().startsWith(lowerCaseOption)) {
                if (arg.length() > option.length() && arg.substring(option.length(), option.length() + 1).equals("=")) {
                    return arg.substring(option.length() + 1);
                } else {
                    return null;
                }
            }
            index++;
        }
        return null;
    }

    // Add working directory variable to capsules string replacement variables.
    @Override
    protected String getVarValue(String var) {
        if (var.equals("baseDirectory")) {
            return baseDir;
        } else {
            return super.getVarValue(var);
        }
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
            List<Path> cp = (List<Path>) super.attribute(attr);
            // Add additional directories of JARs to the classpath (at the end), e.g., for JDBC drivers.
            augmentClasspath(cp, new File(baseDir, "drivers"));
            return (T) cp;
        } else return super.attribute(attr);
    }

    private void augmentClasspath(List<Path> classpath, File dir) {
        try {
            if (dir.exists()) {
                // The following might return null if the directory is not there (we check this already) or if an I/O error occurs.
                for (File file : dir.listFiles()) {
                    addToClasspath(classpath, file);
                }
            } else {
                log(LOG_VERBOSE, "Directory to add in Classpath was not found " + dir.getAbsolutePath());
            }
        } catch (SecurityException | NullPointerException e) {
            log(LOG_QUIET, e);
        }
    }

    private void addToClasspath(List<Path> classpath, File file) {
        try {
            if (file.canRead()) {
                if (file.isFile() && isJAR(file)) {
                    classpath.add(file.toPath().toAbsolutePath());
                } else if (file.isDirectory()) { // Search in nested folders as well. TODO: check for circular symlinks.
                    augmentClasspath(classpath, file);
                }
            } else {
                log(LOG_VERBOSE, "File or directory to add in Classpath could not be read " + file.getAbsolutePath());
            }
        } catch (SecurityException | NullPointerException e) {
            log(LOG_QUIET, e);
        }
    }

    private Boolean isJAR(File file) {
        return file.getName().toLowerCase().endsWith(".jar");
    }
}
