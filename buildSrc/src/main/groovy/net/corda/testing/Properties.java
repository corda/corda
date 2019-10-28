package net.corda.testing;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A single class to hold some of the properties we need to get from the command line
 * in order to store test results in Artifactory.
 */
public class Properties {
    private static final Logger LOG = LoggerFactory.getLogger(Properties.class);

    private static String CORDA_TYPE = "corda"; // corda or enterprise

    /**
     * Get the Corda type.  Used in the tag names when we store in Artifactory.
     *
     * @return either 'corda' or 'enterprise'
     */
    static String getCordaType() {
        return CORDA_TYPE;
    }

    /**
     * Set the Corda (repo) type - either enterprise, or corda (open-source).
     * Used in the tag names when we store in Artifactory.
     *
     * @param cordaType the corda repo type.
     */
    static void setCordaType(@NotNull final String cordaType) {
        CORDA_TYPE = cordaType;
        LOG.warn("Set CORDA_TYPE to {}", CORDA_TYPE);
    }

    /**
     * Get property with logging
     *
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

    /**
     * Get Artifactory username
     *
     * @return the username
     */
    static String getUsername() {
        return getProperty("artifactory.username");
    }

    /**
     * Get Artifactory password
     *
     * @return the password
     */
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
        return getProperty("git.branch").replace('/', '-');
    }

    /**
     * @return the branch that this branch was likely checked out from.
     */
    @NotNull
    static String getTargetGitBranch() {
        return getProperty("git.target.branch").replace('/', '-');
    }
}
