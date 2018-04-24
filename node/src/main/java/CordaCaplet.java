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
        String baseDirOption = getOption(args, "--base-directory");
        this.baseDir = Paths.get((baseDirOption == null) ? "." : baseDirOption).toAbsolutePath().normalize().toString();
        String config = getOption(args, "--config-file");
        File configFile = (config == null) ? new File(baseDir, "node.conf") : new File(config);
        try {
            ConfigParseOptions parseOptions = ConfigParseOptions.defaults().setAllowMissing(false);
            Config defaultConfig = ConfigFactory.parseResources("reference.conf", parseOptions);
            Config baseDirectoryConfig = ConfigFactory.parseMap(Collections.singletonMap("baseDirectory", baseDir));
            Config nodeConfig = ConfigFactory.parseFile(configFile, parseOptions);
            return baseDirectoryConfig.withFallback(nodeConfig).withFallback(defaultConfig).resolve();
        } catch (ConfigException e) {
            log(LOG_QUIET, e);
            return ConfigFactory.empty();
        }
    }

    private String getOption(List<String> args, String option) {
        final String lowerCaseOption = option.toLowerCase();
        int index = 0;
        for (String arg : args) {
            if (arg.toLowerCase().equals(lowerCaseOption)) {
                if (index < args.size() - 1) {
                    return args.get(index + 1);
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
            // Create cordapps directory if it doesn't exist.
            File cordappsDir = new File(baseDir, "cordapps");
            cordappsDir.mkdir();
            if (!cordappsDir.exists()) {
                log(LOG_VERBOSE, "cordapps directory cannot be found");
                System.exit(1);
            }
            // Add additional directories of JARs to the classpath (at the end). e.g. for JDBC drivers.
            augmentClasspath((List<Path>) cp, cordappsDir);
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
        if (dir.exists()) {
            // The following might return null if the directory is not there (we check this already) or if an I/O error occurs.
            File[] files = dir.listFiles();
            if (files != null) { // This is unlikely to happen, but if we don't check, for-loop might fail with NullPointerException.
                for (File file : files) {
                    if (file.isFile() && isJAR(file)) {
                        classpath.add(file.toPath().toAbsolutePath());
                    } else if (file.isDirectory()) { // Search in nested folders as well. TODO: check for circular symlinks.
                        augmentClasspath(classpath, file);
                    }
                }
            } // TODO: shall we log if files is null?
        } else {
            log(LOG_VERBOSE, "Directory to add in Classpath was not found " + dir.getAbsolutePath());
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
