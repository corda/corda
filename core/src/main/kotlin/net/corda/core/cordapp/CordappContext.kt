package net.corda.core.cordapp

import net.corda.core.CordaInternal
import net.corda.core.DeleteForDJVM
import net.corda.core.crypto.SecureHash
import java.lang.UnsupportedOperationException

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
class CordappContext private constructor(
        val cordapp: Cordapp,
        val attachmentId: SecureHash?,
        val classLoader: ClassLoader,
        val config: CordappConfig
) {
    companion object {
        @CordaInternal
        fun create(cordapp: Cordapp, attachmentId: SecureHash?, classLoader: ClassLoader, config: CordappConfig): CordappContext {
            return CordappContext(cordapp, attachmentId, classLoader, config)
        }
    }

    @Deprecated("CordappContexts should not be created. Instead retrieve them using `CordappProvider.getAppContext()`.")
    constructor(
            cordapp: Cordapp,
            attachmentId: SecureHash?,
            classLoader: ClassLoader
    ) : this(cordapp, attachmentId, classLoader, EmptyCordappConfig)

    private object EmptyCordappConfig : CordappConfig {
        override fun exists(path: String): Boolean {
            return false
        }

        override fun get(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getInt(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getLong(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getFloat(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getDouble(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getNumber(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getString(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())

        override fun getBoolean(path: String) = throw CordappConfigException("Cordapp configuration is incorrect", UnsupportedOperationException())
    }
}
