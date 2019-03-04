package net.corda.core.internal.cordapp

import net.corda.core.cordapp.Cordapp
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.warnOnce
import net.corda.core.utilities.loggerFor
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides a way to acquire information about the calling CorDapp.
 */
object CordappResolver {

    private val logger = loggerFor<CordappResolver>()
    private val cordappClasses: ConcurrentHashMap<String, Set<Cordapp>> = ConcurrentHashMap()

    // TODO Use the StackWalker API once we migrate to Java 9+
    private var cordappResolver: () -> Cordapp? = {
        Exception().stackTrace
                .mapNotNull { cordappClasses[it.className] }
                // in case there are multiple classes matched, we select the first one having a single CorDapp registered against it.
                .firstOrNull { it.size == 1 }
                // otherwise we return null, signalling we cannot reliably determine the current CorDapp.
                ?.single()
    }

    /*
     * Associates class names with CorDapps or logs a warning when a CorDapp is already registered for a given class.
     * This could happen when trying to run different versions of the same CorDapp on the same node.
     */
    @Synchronized
    fun register(cordapp: Cordapp) {
        val contractClasses = cordapp.contractClassNames.toSet()
        val existingClasses = cordappClasses.keys
        val classesToRegister = cordapp.cordappClasses.toSet()
        val notAlreadyRegisteredClasses = classesToRegister - existingClasses
        val alreadyRegistered= HashMap(cordappClasses).apply { keys.retainAll(classesToRegister) }

        notAlreadyRegisteredClasses.forEach { cordappClasses[it] = setOf(cordapp) }

        for ((className, registeredCordapps) in alreadyRegistered) {
            if (registeredCordapps.any { it.jarHash == cordapp.jarHash }) continue
            if (className in contractClasses) {
                logger.warnOnce("More than one CorDapp registered for contract $className.")
            }
            cordappClasses[className] = registeredCordapps + cordapp
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
    val currentCordapp: Cordapp? get() = cordappResolver()

    /**
     * Returns the target version of the current calling CorDapp. Defaults to platform version 1 if there isn't one,
     * assuming only basic platform capabilities.
     */
    val currentTargetVersion: Int get() = currentCordapp?.targetPlatformVersion ?: 1

    /**
     * Temporarily apply a fake CorDapp with the given parameters. For use in testing.
     */
    @Synchronized
    @VisibleForTesting
    fun <T> withCordapp(minimumPlatformVersion: Int = 1, targetPlatformVersion: Int = PLATFORM_VERSION, block: () -> T): T {
        val currentResolver = cordappResolver
        cordappResolver = {
            CordappImpl.TEST_INSTANCE.copy(minimumPlatformVersion = minimumPlatformVersion, targetPlatformVersion = targetPlatformVersion)
        }
        try {
            return block()
        } finally {
            cordappResolver = currentResolver
        }
    }

    @VisibleForTesting
    internal fun clear() {
        cordappClasses.clear()
    }
}