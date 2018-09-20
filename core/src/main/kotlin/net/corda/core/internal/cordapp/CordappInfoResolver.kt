package net.corda.core.internal.cordapp

import net.corda.core.utilities.loggerFor
import java.util.concurrent.ConcurrentHashMap


/**
 * Provides a way to find out if code is called from a CorDapp and if yes, acquire information  about the calling
 * CorDapp.
 */
object CordappInfoResolver {
    private val logger = loggerFor<CordappInfoResolver>()
    private val cordappClasses: ConcurrentHashMap<String, CordappImpl.Info> = ConcurrentHashMap()

    // TODO use the StackWalker API once we migrate to Java 9+
    private var cordappInfoResolver: () -> CordappImpl.Info? = { Exception().stackTrace.mapNotNull { cordappClasses[it.className] }.firstOrNull() }

    /*
     * Associates class names with CorDapps or logs a warning when a CorDapp is already registered for a given class.
     * This could happen when trying to run different versions of the same CorDapp on the same node.
     */
    @Synchronized
    fun register(classes: List<String>, cordapp: CordappImpl.Info) {
        classes.forEach {
            if (cordappClasses.contains(it)) {
               logger.warn("Not registering ${cordapp.shortName} for $it, a different CorDapp (${cordappClasses[it]?.shortName}) has already been registered for this class.")
            } else {
                cordappClasses[it] = cordapp
            }
        }
    }

    /*
     * This should only be used when making a change that would break compatibility with existing CorDapps. The change
     * can then be version-gated, meaning the old behaviour is used if the calling CorDapp's target version is lower
     * than the platform version that introduces the new behaviour.
     * In situations where a `[CordappProvider]` is available the CorDapp context should be obtained from there.
     *
     * @return Information about the CorDapp from which the invoker is called, null if called outside a CorDapp.
     */
    fun getCorDappInfo(): CordappImpl.Info? = cordappInfoResolver()

    /**
     * Temporarily switch out the internal resolver for another one. For use in testing.
     */
    @Synchronized
    fun withCordappInfoResolution(tempResolver: () -> CordappImpl.Info?, block: () -> Unit) {
        val resolver = cordappInfoResolver
        cordappInfoResolver = tempResolver
        block()
        cordappInfoResolver = resolver
    }
}