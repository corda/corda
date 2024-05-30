// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;
import com.typesafe.config.ConfigValue;
import sun.misc.Signal;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.typesafe.config.ConfigUtil.splitPath;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class CordaCaplet extends Capsule {
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
        return (config == null || config.isEmpty()) ? new File(baseDir, "node.conf") : new File(config);
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
                if (arg.length() > option.length() && arg.charAt(option.length()) == '=') {
                    return arg.substring(option.length() + 1);
                } else {
                    return null;
                }
            }
            index++;
        }
        return null;
    }

    @Override
    protected ProcessBuilder prelaunch(List<String> jvmArgs, List<String> args) {
        checkJavaVersion();
        nodeConfig = parseConfigFile(args);
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
        List<String> args = pb.command();
        args.addAll(1, getNodeJvmArgs());
        pb.command(args);
        return super.launch(pb);
    }

    private List<String> getNodeJvmArgs() throws IOException {
        try (InputStream resource = requireNonNull(getClass().getResourceAsStream("/node-jvm-args.txt"))) {
            return new BufferedReader(new InputStreamReader(resource)).lines().collect(toList());
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
            T cp = super.attribute(attr);

            File cordappsDir = new File(baseDir, "cordapps");
            // Create cordapps directory if it doesn't exist.
            if (!checkIfCordappDirExists(cordappsDir)) {
                // If it fails, just return the existing class path. The main Corda jar will detect the error and fail gracefully.
                return cp;
            }

            // Add additional directories of JARs to the classpath (at the end), e.g., for JDBC drivers.
            augmentClasspath((List<Path>) cp, Path.of(baseDir, "drivers"));
            try {
                List<String> jarDirs = nodeConfig.getStringList("jarDirs");
                log(LOG_VERBOSE, "Configured JAR directories = " + jarDirs);
                for (String jarDir : jarDirs) {
                    augmentClasspath((List<Path>) cp, Path.of(jarDir));
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
            return (T) jvmArgs;
        } else if (ATTR_SYSTEM_PROPERTIES == attr) {
            // Add system properties, if specified, from the config.
            Map<String, String> systemProps = new LinkedHashMap<>((Map<String, String>) super.attribute(attr));
            try {
                Map<String, ?> overrideSystemProps = nodeConfig.getConfig("systemProperties").entrySet().stream()
                    .map(Property::create)
                    .collect(toMap(Property::key, Property::value));
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

    private void augmentClasspath(List<Path> classpath, Path dir) {
        if (Files.exists(dir)) {
            try (var files = Files.list(dir)) {
                files.forEach((file) -> addToClasspath(classpath, file));
            } catch (IOException e) {
                log(LOG_QUIET, e);
            }
        } else {
            log(LOG_VERBOSE, "Directory to add in Classpath was not found " + dir.toAbsolutePath());
        }
    }

    private static void checkJavaVersion() {
        String version = System.getProperty("java.version");
        if (version == null || Stream.of("17").noneMatch(version::startsWith)) {
            System.err.printf("Error: Unsupported Java version %s; currently only version 17 is supported.\n", version);
            System.exit(1);
        }
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

    private void addToClasspath(List<Path> classpath, Path file) {
        try {
            if (Files.isReadable(file)) {
                if (Files.isRegularFile(file) && isJAR(file)) {
                    classpath.add(file.toAbsolutePath());
                } else if (Files.isDirectory(file)) { // Search in nested folders as well. TODO: check for circular symlinks.
                    augmentClasspath(classpath, file);
                }
            } else {
                log(LOG_VERBOSE, "File or directory to add in Classpath could not be read " + file.toAbsolutePath());
            }
        } catch (SecurityException e) {
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

    private Boolean isJAR(Path file) {
        return file.toString().toLowerCase().endsWith(".jar");
    }

    /**
     * Helper class so that we can parse the "systemProperties" element of node.conf.
     */
    private record Property(String key, Object value) {
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
