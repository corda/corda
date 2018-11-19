package net.corda.core.internal.cordapp

import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.VisibleForTesting
import net.corda.core.utilities.loggerFor
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides a way to acquire information about the calling CorDapp.
 */
object CordappInfoResolver {
    private val logger = loggerFor<CordappInfoResolver>()
    private val cordappClasses: ConcurrentHashMap<String, Set<CordappImpl.Info>> = ConcurrentHashMap()

    // TODO Use the StackWalker API once we migrate to Java 9+
    private var cordappInfoResolver: () -> CordappImpl.Info? = {
        Exception().stackTrace
                .mapNotNull { cordappClasses[it.className] }
                // If there is more than one cordapp registered for a class name we can't determine the "correct" one and return null.
                .firstOrNull { it.size == 1 }?.single()
    }

    /*
     * Associates class names with CorDapps or logs a warning when a CorDapp is already registered for a given class.
     * This could happen when trying to run different versions of the same CorDapp on the same node.
     */
    @Synchronized
    fun register(classes: List<String>, cordapp: CordappImpl.Info) {
        classes.forEach {
            if (cordappClasses.containsKey(it)) {
                logger.warn("More than one CorDapp registered for $it.")
                cordappClasses[it] = cordappClasses[it]!! + cordapp
            } else {
                cordappClasses[it] = setOf(cordapp)
            }
        }
    }

    /*
     * This should only be used when making a change that would break compatibility with existing CorDapps. The change
     * can then be version-gated, meaning the old behaviour is used if the calling CorDapp's target version is lower
     * than the platform version that introduces the new behaviour.
     * In situations where a `[CordappProvider]` is available the CorDapp context should be obtained from there.
     *
     * @return Information about the CorDapp from which the invoker is called, null if called outside a CorDapp or the
     * calling CorDapp cannot be reliably determined.
     */
    val currentCordappInfo: CordappImpl.Info? get() = cordappInfoResolver()

    /**
     * Returns the target version of the current calling CorDapp. Defaults to the current platform version if there isn't one.
     */
    // TODO It may be the default is wrong and this should be Int? instead
    val currentTargetVersion: Int get() = currentCordappInfo?.targetPlatformVersion ?: PLATFORM_VERSION

    /**
     * Temporarily apply a fake CorDapp.Info with the given parameters. For use in testing.
     */
    @Synchronized
    @VisibleForTesting
    fun <T> withCordappInfo(shortName: String = "CordappInfoResolver.withCordappInfo",
                            vendor: String = "Corda",
                            version: String = "1.0",
                            minimumPlatformVersion: Int = 1,
                            targetPlatformVersion: Int = PLATFORM_VERSION,
                            block: () -> T): T {
        val currentResolver = cordappInfoResolver
        cordappInfoResolver = { CordappImpl.Info(shortName, vendor, version, minimumPlatformVersion, targetPlatformVersion) }
        try {
            return block()
        } finally {
            cordappInfoResolver = currentResolver
        }
    }

    @VisibleForTesting
    internal fun clear() {
        cordappClasses.clear()
    }
}
