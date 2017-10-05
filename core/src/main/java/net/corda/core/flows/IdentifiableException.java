package net.corda.core.flows;

import javax.annotation.Nullable;

/**
 * An exception that may be identified with an ID. If an exception originates in a counter-flow this ID will be
 * propagated. This allows correlation of error conditions across different flows.
 */
public interface IdentifiableException {
    /**
     * @return the ID of the error, or null if the error doesn't have it set (yet).
     */
    default @Nullable Long getErrorId() {
        return null;
    }
}
