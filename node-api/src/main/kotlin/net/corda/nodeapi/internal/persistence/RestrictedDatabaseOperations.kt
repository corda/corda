package net.corda.nodeapi.internal.persistence

import net.corda.core.internal.PlatformVersionSwitches.RESTRICTED_DATABASE_OPERATIONS
import net.corda.core.internal.warnOnce
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RestrictedDatabaseOperations")

internal inline fun <T> restrictDatabaseOperationFromJdbcSession(method: String, serviceHub: ServiceHub, operation: () -> T): T {
    return restrictDatabaseOperation("ServiceHub.jdbcSession.$method", serviceHub, operation)
}

internal inline fun <T> restrictDatabaseOperationFromEntityManager(method: String, serviceHub: ServiceHub, operation: () -> T): T {
    return restrictDatabaseOperation("ServiceHub.withEntityManager.$method", serviceHub, operation)
}

internal inline fun <T> restrictDatabaseOperation(method: String, serviceHub: ServiceHub, operation: () -> T): T {
    return if (serviceHub.getAppContext().cordapp.targetPlatformVersion >= RESTRICTED_DATABASE_OPERATIONS) {
        throw UnsupportedOperationException("$method is restricted and cannot be called")
    } else {
        log.warnOnce(
            "$method should not be called, as manipulating database transactions and connections breaks the Corda flow state machine in " +
                    "ways that only become evident in failure scenarios. Purely for API backwards compatibility reasons, the prior " +
                    "behaviour is continued for target platform versions less than $RESTRICTED_DATABASE_OPERATIONS. You should evolve " +
                    "the CorDapp away from using these problematic APIs as soon as possible. For target platform version of " +
                    "$RESTRICTED_DATABASE_OPERATIONS or above, an exception will be thrown instead."
        )
        operation()
    }
}