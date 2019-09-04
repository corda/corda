import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigParseOptions;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BridgeCaplet extends Capsule {
    private Config bridgeConfig = null;
    private String baseDir = null;

    protected BridgeCaplet(Capsule pred) {
        super(pred);
    }

    private Config parseConfigFile(List<String> args) {
        String baseDirOption = getOptionMultiple(args, Arrays.asList("--base-directory", "-b"));
        // Ensure consistent behaviour with NodeArgsParser.kt, see CORDA-1598.
        if (null == baseDirOption || baseDirOption.isEmpty()) {
            baseDirOption = getOption(args, "-base-directory");
        }
        this.baseDir = Paths.get((baseDirOption == null) ? "." : baseDirOption).toAbsolutePath().normalize().toString();
        String config = getOption(args, "--config-file");
        // Same as for baseDirOption.
        if (null == config || config.isEmpty()) {
            config = getOption(args, "-config-file");
        }
        File configFile = new File(baseDir, "firewall.conf");
        if (config != null) {
            configFile = new File(config);
        } else if (!configFile.exists()) {
            File oldConfigFile = new File(baseDir, "bridge.conf");
            if (oldConfigFile.exists()) {
                configFile = oldConfigFile;
            }
        }
        try {
            ConfigParseOptions parseOptions = ConfigParseOptions.defaults().setAllowMissing(false);
            Config defaultConfig = ConfigFactory.parseResources("firewalldefault_latest.conf", parseOptions);
            Config baseDirectoryConfig = ConfigFactory.parseMap(Collections.singletonMap("baseDirectory", baseDir));
            Config nodeConfig = ConfigFactory.parseFile(configFile, parseOptions);
            return baseDirectoryConfig.withFallback(nodeConfig).withFallback(defaultConfig).resolve();
        } catch (ConfigException e) {
            log(LOG_QUIET, e);
            return ConfigFactory.empty();
        }
    }

    @Override
    protected ProcessBuilder prelaunch(List<String> jvmArgs, List<String> args) {
        this.bridgeConfig = parseConfigFile(args);
        return super.prelaunch(jvmArgs, args);
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
        } else if (ATTR_JVM_ARGS == attr) {
            // Read JVM args from the config if specified, else leave alone.
            List<String> jvmArgs = new ArrayList<>((List<String>) super.attribute(attr));
            boolean defaultOutOfMemoryErrorHandling = true;
            try {
                List<String> configJvmArgs = bridgeConfig.getStringList("custom.jvmArgs");
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
