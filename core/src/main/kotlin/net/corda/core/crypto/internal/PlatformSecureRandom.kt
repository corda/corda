@file:JvmName("PlatformSecureRandom")
@file:DeleteForDJVM
package net.corda.core.crypto.internal

import net.corda.core.DeleteForDJVM
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
