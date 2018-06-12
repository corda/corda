/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.flows;

import net.corda.core.KeepForDJVM;

import javax.annotation.Nullable;

/**
 * An exception that may be identified with an ID. If an exception originates in a counter-flow this ID will be
 * propagated. This allows correlation of error conditions across different flows.
 */
@KeepForDJVM
public interface IdentifiableException {
    /**
     * @return the ID of the error, or null if the error doesn't have it set (yet).
     */
    @Nullable
    default Long getErrorId() {
        return null;
    }
}
