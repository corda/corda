/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */
@file:JvmName("PlatformSecureRandom")
package net.corda.core.crypto.internal

import net.corda.core.crypto.CORDA_SECURE_RANDOM_ALGORITHM
import net.corda.core.crypto.DummySecureRandom
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.SgxSupport
import java.security.SecureRandom

/**
 * This has been migrated into a separate class so that it
 * is easier to delete from the core-deterministic module.
 */
@VisibleForTesting
internal val platformSecureRandom = when {
    SgxSupport.isInsideEnclave -> DummySecureRandom
    else -> SecureRandom.getInstance(CORDA_SECURE_RANDOM_ALGORITHM)
}
