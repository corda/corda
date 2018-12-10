// Due to Capsule being in the default package, which cannot be imported, this caplet
// must also be in the default package. When using Kotlin there are a whole host of exceptions
// trying to construct this from Capsule, so it is written in Java.

import com.typesafe.config.*;
import sun.misc.Signal;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CordaCaplet extends Capsule {

    private Config nodeConfig = null;
    private String baseDir = null;

    protected CordaCaplet(Capsule pred) {
        super(pred);
    }

    private Config parseConfigFile(List<String> args) {
        this.baseDir = getBaseDirectory(args);
        String config = getOption(args, "--config-file");
        File configFile = (config == null) ? new File(baseDir, "node.conf") : new File(config);
        try {
            ConfigParseOptions parseOptions = ConfigParseOptions.defaults().setAllowMissing(false);
            Config defaultConfig = ConfigFactory.parseResources("reference.conf", parseOptions);
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
            try {
                List<String> configJvmArgs = nodeConfig.getStringList("custom.jvmArgs");
                jvmArgs.clear();
                jvmArgs.addAll(configJvmArgs);
                log(LOG_VERBOSE, "Configured JVM args = " + jvmArgs);
            } catch (ConfigException.Missing e) {
                // Ignore since it's ok to be Missing. Other errors would be unexpected.
            } catch (ConfigException e) {
                log(LOG_QUIET, e);
            }
            return (T) jvmArgs;
        } else if (ATTR_SYSTEM_PROPERTIES == attr) {
            // Add system properties, if specified, from the config.
            Map<String, String> systemProps = new LinkedHashMap<>((Map<String, String>) super.attribute(attr));
            try {
                Config overrideSystemProps = nodeConfig.getConfig("systemProperties");
                log(LOG_VERBOSE, "Configured system properties = " + overrideSystemProps);
                for (Map.Entry<String, ConfigValue> entry : overrideSystemProps.entrySet()) {
                    systemProps.put(entry.getKey(), entry.getValue().unwrapped().toString());
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
        if (version == null || !version.startsWith("1.8")) {
            System.err.printf("Error: Unsupported Java version %s; currently only version 1.8 is supported.\n", version);
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
}
