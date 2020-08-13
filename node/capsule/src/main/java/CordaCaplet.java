// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import com.typesafe.config.*;
import sun.misc.Signal;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.stream.Stream;

import static com.typesafe.config.ConfigUtil.splitPath;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.stream.Collectors.toMap;

public class CordaCaplet extends Capsule {
    private static final String DJVM_DIR ="djvm";

    private Config nodeConfig = null;
    private String baseDir = null;

    protected CordaCaplet(Capsule pred) {
        super(pred);
    }

    private Config parseConfigFile(List<String> args) {
        this.baseDir = getBaseDirectory(args);

        File configFile = getConfigFile(args, baseDir);
        try {
            ConfigParseOptions parseOptions = ConfigParseOptions.defaults().setAllowMissing(false);
            Config defaultConfig = ConfigFactory.parseResources("corda-reference.conf", parseOptions);
            Config baseDirectoryConfig = ConfigFactory.parseMap(Collections.singletonMap("baseDirectory", baseDir));
            Config nodeConfig = ConfigFactory.parseFile(configFile, parseOptions);
            return baseDirectoryConfig.withFallback(nodeConfig).withFallback(defaultConfig).resolve();
        } catch (ConfigException e) {
            log(LOG_DEBUG, e);
            return ConfigFactory.empty();
        }
    }

    File getConfigFile(List<String> args, String baseDir) {
        String config = getOptionMultiple(args, Arrays.asList("--config-file", "-f"));
        return (config == null || config.equals("")) ? new File(baseDir, "node.conf") : new File(config);
    }

    String getBaseDirectory(List<String> args) {
        String baseDir = getOptionMultiple(args, Arrays.asList("--base-directory", "-b"));
        return Paths.get((baseDir == null) ? "." : baseDir).toAbsolutePath().normalize().toString();
    }

    private String getOptionMultiple(List<String> args, List<String> possibleOptions) {
        String result = null;
        for(String option: possibleOptions) {
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

    private void installDJVM() {
        Path djvmDir = Paths.get(baseDir, DJVM_DIR);
        if (!djvmDir.toFile().mkdir() && !Files.isDirectory(djvmDir)) {
            log(LOG_VERBOSE, "DJVM directory could not be created");
        } else {
            try {
                Path sourceDir = appDir().resolve(DJVM_DIR);
                if (Files.isDirectory(sourceDir)) {
                    installCordaDependenciesForDJVM(sourceDir, djvmDir);
                    installTransitiveDependenciesForDJVM(appDir(), djvmDir);
                }
            } catch (IOException e) {
                log(LOG_VERBOSE, "Failed to populate directory " + djvmDir.toAbsolutePath());
                log(LOG_VERBOSE, e);
            }
        }
    }

    private void installCordaDependenciesForDJVM(Path sourceDir, Path targetDir) throws IOException {
        try (DirectoryStream<Path> directory = Files.newDirectoryStream(sourceDir, file -> Files.isRegularFile(file))) {
            for (Path sourceFile : directory) {
                Path targetFile = targetDir.resolve(sourceFile.getFileName());
                installFile(sourceFile, targetFile);
            }
        }
    }

    private void installTransitiveDependenciesForDJVM(Path sourceDir, Path targetDir) throws IOException {
        Manifest manifest = getManifest();
        String[] transitives = manifest.getMainAttributes().getValue("Corda-DJVM-Dependencies").split("\\s++", 0);
        for (String transitive : transitives) {
            Path source = sourceDir.resolve(transitive);
            if (Files.isRegularFile(source)) {
                installFile(source, targetDir.resolve(transitive));
            }
        }
    }

    private Manifest getManifest() throws IOException {
        URL capsule = getClass().getProtectionDomain().getCodeSource().getLocation();
        try (JarInputStream jar = new JarInputStream(capsule.openStream())) {
            return jar.getManifest();
        }
    }

    private void installFile(Path source, Path target) {
        try {
            // Forcibly reinstall this dependency.
            Files.deleteIfExists(target);
            Files.createSymbolicLink(target, source);
        } catch (UnsupportedOperationException | IOException e) {
            copyFile(source, target);
        }
    }

    private void copyFile(Path source, Path target) {
        try {
            Files.copy(source, target, REPLACE_EXISTING);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            target.toFile().delete();
            log(LOG_VERBOSE, e);
        }
    }

    @Override
    protected ProcessBuilder prelaunch(List<String> jvmArgs, List<String> args) {
        checkJavaVersion();
        nodeConfig = parseConfigFile(args);
        installDJVM();
        return super.prelaunch(jvmArgs, args);
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

    // Capsule does not handle multiple instances of same option hence we add in the args here to process builder
    // For multiple instances Capsule jvm args handling works on basis that one overrides the other.
    @Override
    protected int launch(ProcessBuilder pb) throws IOException, InterruptedException {
        if (isAtLeastJavaVersion11()) {
            List<String> args = pb.command();
            List<String> myArgs = Arrays.asList(
                "--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.time=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.nio=ALL-UNNAMED");
            args.addAll(1, myArgs);
            pb.command(args);
        }
        return super.launch(pb);
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

            File cordappsDir = new File(baseDir, "cordapps");
            // Create cordapps directory if it doesn't exist.
            if (!checkIfCordappDirExists(cordappsDir)) {
                // If it fails, just return the existing class path. The main Corda jar will detect the error and fail gracefully.
                return cp;
            }

            // Add additional directories of JARs to the classpath (at the end), e.g., for JDBC drivers.
            augmentClasspath((List<Path>) cp, new File(baseDir, "drivers"));
            try {
                List<String> jarDirs = nodeConfig.getStringList("jarDirs");
                log(LOG_VERBOSE, "Configured JAR directories = " + jarDirs);
                for (String jarDir : jarDirs) {
                    augmentClasspath((List<Path>) cp, new File(jarDir));
                }
            } catch (ConfigException.Missing e) {
                // Ignore since it's ok to be Missing. Other errors would be unexpected.
            } catch (ConfigException e) {
                log(LOG_QUIET, e);
            }
            return cp;
        } else if (ATTR_JVM_ARGS == attr) {
            // Read JVM args from the config if specified, else leave alone.
            List<String> jvmArgs = new ArrayList<>((List<String>) super.attribute(attr));
            boolean defaultOutOfMemoryErrorHandling = true;
            try {
                List<String> configJvmArgs = nodeConfig.getStringList("custom.jvmArgs");
                jvmArgs.clear();
                jvmArgs.addAll(configJvmArgs);
                log(LOG_VERBOSE, "Configured JVM args = " + jvmArgs);

                // Switch off default OutOfMemoryError handling if any related JVM arg is specified in custom config
                defaultOutOfMemoryErrorHandling = configJvmArgs.stream().noneMatch(arg -> arg.contains("OutOfMemoryError"));
            } catch (ConfigException.Missing e) {
                // Ignore since it's ok to be Missing. Other errors would be unexpected.
            } catch (ConfigException e) {
                log(LOG_QUIET, e);
            }
            // Shutdown and print diagnostics on OutOfMemoryError.
            if (defaultOutOfMemoryErrorHandling) {
                jvmArgs.add("-XX:+HeapDumpOnOutOfMemoryError");
                jvmArgs.add("-XX:+CrashOnOutOfMemoryError");
            }
            if (isAtLeastJavaVersion11()) {
                jvmArgs.add("-Dnashorn.args=--no-deprecation-warning");
            }
            return (T) jvmArgs;
        } else if (ATTR_SYSTEM_PROPERTIES == attr) {
            // Add system properties, if specified, from the config.
            Map<String, String> systemProps = new LinkedHashMap<>((Map<String, String>) super.attribute(attr));
            try {
                Map<String, ?> overrideSystemProps = nodeConfig.getConfig("systemProperties").entrySet().stream()
                    .map(Property::create)
                    .collect(toMap(Property::getKey, Property::getValue));
                log(LOG_VERBOSE, "Configured system properties = " + overrideSystemProps);
                for (Map.Entry<String, ?> entry : overrideSystemProps.entrySet()) {
                    systemProps.put(entry.getKey(), entry.getValue().toString());
                }
            } catch (ConfigException.Missing e) {
                // Ignore since it's ok to be Missing. Other errors would be unexpected.
            } catch (ConfigException e) {
                log(LOG_QUIET, e);
            }
            return (T) systemProps;
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

    private static void checkJavaVersion() {
        String version = System.getProperty("java.version");
        if (version == null || Stream.of("1.8", "11").noneMatch(version::startsWith)) {
            System.err.printf("Error: Unsupported Java version %s; currently only version 1.8 or 11 is supported.\n", version);
            System.exit(1);
        }
    }

    private static boolean isAtLeastJavaVersion11() {
        String version = System.getProperty("java.specification.version");
        if (version != null) {
            return Float.parseFloat(version) >= 11f;
        }
        return false;
    }

    private Boolean checkIfCordappDirExists(File dir) {
        try {
            if (!dir.mkdir() && !dir.exists()) { // It is unlikely to enter this if-branch, but just in case.
                logOnFailedCordappDir();
                return false;
            }
        }
        catch (SecurityException | NullPointerException e) {
            logOnFailedCordappDir();
            return false;
        }
        return true;
    }

    private void logOnFailedCordappDir() {
        log(LOG_VERBOSE, "Cordapps dir could not be created");
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

    @Override
    protected void liftoff() {
        super.liftoff();
        Signal.handle(new Signal("INT"), signal -> {
            // Disable Ctrl-C for this process, so the child process can handle it in the shell instead.
        });
    }

    private Boolean isJAR(File file) {
        return file.getName().toLowerCase().endsWith(".jar");
    }

    /**
     * Helper class so that we can parse the "systemProperties" element of node.conf.
     */
    private static class Property {
        private final String key;
        private final Object value;

        Property(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        String getKey() {
            return key;
        }

        Object getValue() {
            return value;
        }

        static Property create(Map.Entry<String, ConfigValue> entry) {
            // String.join is preferred here over Typesafe's joinPath method, as the joinPath method would put quotes around the system
            // property key which is undesirable here.
            return new Property(
                    String.join(".", splitPath(entry.getKey())),
                    entry.getValue().unwrapped()
            );
        }
    }
}
