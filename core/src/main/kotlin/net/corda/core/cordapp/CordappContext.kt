package net.corda.core.cordapp

import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.SecureHash

/**
 * An app context provides information about where an app was loaded from, access to its classloader,
 * and (in the included [Cordapp] object) lists of annotated classes discovered via scanning the JAR.
 *
 * A CordappContext is obtained from [CordappProvider.getAppContext] which resides on a [ServiceHub]. This will be
 * used primarily from within flows.
 *
 * @property cordapp The cordapp this context is about
 * @property attachmentId For CorDapps containing [Contract] or [UpgradedContract] implementations this will be populated
 * with the attachment containing those class files
 * @property classLoader the classloader used to load this cordapp's classes
 * @property config Configuration for this CorDapp
 */
@DeleteForDJVM
class CordappContext internal constructor(
        val cordapp: Cordapp,
        val attachmentId: SecureHash?,
        val classLoader: ClassLoader,
        val config: CordappConfig
)
