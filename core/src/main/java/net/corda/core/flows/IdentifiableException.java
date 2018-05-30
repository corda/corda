package net.corda.core.flows;

import net.corda.core.Deterministic;

import javax.annotation.Nullable;

/**
 * An exception that may be identified with an ID. If an exception originates in a counter-flow this ID will be
 * propagated. This allows correlation of error conditions across different flows.
 */
@Deterministic
public interface IdentifiableException {
    /**
     * @return the ID of the error, or null if the error doesn't have it set (yet).
     */
    @Nullable
    default Long getErrorId() {
        return null;
    }
}
