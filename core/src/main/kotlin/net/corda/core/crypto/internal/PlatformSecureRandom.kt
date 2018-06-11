@file:JvmName("PlatformSecureRandom")
@file:DeleteForDJVM
package net.corda.core.crypto.internal

import net.corda.core.DeleteForDJVM
import org.apache.commons.lang.SystemUtils
import java.security.SecureRandom

/**
 * This has been migrated into a separate class so that it
 * is easier to delete from the core-deterministic module.
 */
internal val platformSecureRandom: () -> SecureRandom = when {
    SystemUtils.IS_OS_LINUX -> {
        { SecureRandom.getInstance("NativePRNGNonBlocking") }
    }
    else -> SecureRandom::getInstanceStrong
}
