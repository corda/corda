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

    private val insideInMemoryTest: Boolean by lazy { insideInMemoryTest() }

    // TODO Use the StackWalker API once we migrate to Java 9+
    private var cordappResolver: () -> Cordapp? = {
        Exception().stackTrace
                .mapNotNull { cordappClasses[it.className] }
                // in case there are multiple classes matched, we select the first one having a single CorDapp registered against it.
                .firstOrNull { it.size == 1 }
                // otherwise we return null, signalling we cannot reliably determine the current CorDapp.
                ?.single()
    }

    /**
     * Associates class names with CorDapps or logs a warning when a CorDapp is already registered for a given class.
     * This could happen when trying to run different versions of the same CorDapp on the same node.
     *
     * @throws IllegalStateException when multiple CorDapps are registered for the same contract class,
     *          since this can lead to undefined behaviour.
     */
    @Synchronized
    fun register(cordapp: Cordapp) {
        val contractClasses = cordapp.contractClassNames.toSet()
        val existingClasses = cordappClasses.keys
        val classesToRegister = cordapp.cordappClasses.toSet()
        val notAlreadyRegisteredClasses = classesToRegister - existingClasses
        val alreadyRegistered= HashMap(cordappClasses).apply { keys.retainAll(classesToRegister) }

        notAlreadyRegisteredClasses.forEach { cordappClasses[it] = setOf(cordapp) }

        for ((registeredClassName, registeredCordapps) in alreadyRegistered) {
            val duplicateCordapps = registeredCordapps.filter { it.jarHash == cordapp.jarHash }.toSet()

            if (duplicateCordapps.isNotEmpty()) {
                logger.warnOnce("The CorDapp (name: ${cordapp.info.shortName}, file: ${cordapp.name}) " +
                        "is installed multiple times on the node. The following files correspond to the exact same content: " +
                        "${duplicateCordapps.map { it.name }}")
                continue
            }
            // During in-memory tests, the spawned nodes share the same CordappResolver, so detected conflicts can be spurious.
            if (registeredClassName in contractClasses && !insideInMemoryTest) {
                    throw IllegalStateException("More than one CorDapp installed on the node for contract $registeredClassName. " +
                            "Please remove the previous version when upgrading to a new version.")
            }

            cordappClasses[registeredClassName] = registeredCordapps + cordapp
        }
    }

    private fun insideInMemoryTest(): Boolean {
        return Exception().stackTrace.any {
            it.className.startsWith("net.corda.testing.node.internal.InternalMockNetwork") ||
            it.className.startsWith("net.corda.testing.node.internal.InProcessNode") ||
            it.className.startsWith("net.corda.testing.node.MockServices")
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

    // A list of extra CorDapps added to the current CorDapps list for testing purposes.
    private var extraCordappsForTesting = listOf<Cordapp>()

    /**
     * Return all the CorDapps that were involved in the call stack at the point the provided exception was generated.
     *
     * This is provided to allow splitting the cost of generating the exception and retrieving the CorDapps involved.
     */
    fun cordappsFromException(exception: Exception): List<Cordapp> {
        val apps = exception.stackTrace
                .mapNotNull { cordappClasses[it.className] }
                .flatten()
                .distinct()
        return (apps + extraCordappsForTesting)
    }

    /**
     * Temporarily apply a fake CorDapp with the given parameters. For use in testing.
     */
    @Synchronized
    @VisibleForTesting
    fun <T> withTestCordapp(minimumPlatformVersion: Int = 1,
                            targetPlatformVersion: Int = PLATFORM_VERSION,
                            extraApps: List<CordappImpl> = listOf(),
                            block: () -> T): T {
        val currentResolver = cordappResolver
        cordappResolver = {
            CordappImpl.TEST_INSTANCE.copy(minimumPlatformVersion = minimumPlatformVersion, targetPlatformVersion = targetPlatformVersion)
        }
        extraCordappsForTesting = listOf(cordappResolver()!!) + extraApps
        try {
            return block()
        } finally {
            cordappResolver = currentResolver
            extraCordappsForTesting = listOf()
        }
    }

    @VisibleForTesting
    internal fun clear() {
        cordappClasses.clear()
    }
}