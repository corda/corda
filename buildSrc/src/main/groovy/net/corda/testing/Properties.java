package net.corda.testing;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Properties {
    private static final Logger LOG = LoggerFactory.getLogger(Properties.class);

    private static String CORDA_TYPE = "corda"; // corda or enterprise

    static void setCordaType(@NotNull final String cordaType) {
        CORDA_TYPE = cordaType;
    }

    static String getCordaType() { return CORDA_TYPE; }

    /**
     * Get property with logging
     * @param key property to get
     * @return empty string, or trimmed value
     */
    @NotNull
    static String getProperty(@NotNull final String key) {
        final String value = System.getProperty(key, "").trim();
        if (value.isEmpty()) {
            LOG.warn("Property '{}' not set", key);
        } else {
            LOG.debug("Ok.  Property '{}' is set", key);
        }
        return value;
    }

    static String getUsername() {
        return getProperty("artifactory.username");
    }

    static String getPassword() {
        return getProperty("artifactory.password");
    }

    /**
     * The current branch/tag
     *
     * @return the current branch
     */
    @NotNull
    static String getGitBranch() {
        return (getCordaType() + "-" + getProperty("git.branch"))
                .replace('/', '-');
    }

    /**
     * @return the branch that this branch was likely checked out from.
     */
    @NotNull
    static String getTargetGitBranch() {
        return (getCordaType() + "-" + getProperty("git.target.branch"))
                .replace('/', '-');
    }
}
