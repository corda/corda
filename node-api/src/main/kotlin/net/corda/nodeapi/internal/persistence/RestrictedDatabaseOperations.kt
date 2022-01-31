package net.corda.nodeapi.internal.persistence

import net.corda.core.internal.PlatformVersionSwitches.RESTRICTED_DATABASE_OPERATIONS
import net.corda.core.internal.warnOnce
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("RestrictedDatabaseOperations")

internal inline fun <T> restrictDatabaseOperationFromJdbcSession(serviceHub: ServiceHub, operation: () -> T): T {
    return restrictDatabaseOperation("ServiceHub.jdbcSession", serviceHub, operation)
}

internal inline fun <T> restrictDatabaseOperationFromEntityManager(serviceHub: ServiceHub, operation: () -> T): T {
    return restrictDatabaseOperation("ServiceHub.withEntityManager", serviceHub, operation)
}

internal inline fun <T> restrictDatabaseOperation(name: String, serviceHub: ServiceHub, operation: () -> T): T {
    return if (serviceHub.getAppContext().cordapp.targetPlatformVersion >= RESTRICTED_DATABASE_OPERATIONS) {
        throw UnsupportedOperationException("This method cannot be called via $name")
    } else {
        log.warnOnce(
            "This method should not be called via $name, but allowing as the target platform version is less than " +
                    "$RESTRICTED_DATABASE_OPERATIONS. In later versions, with a target platform version of " +
                    "$RESTRICTED_DATABASE_OPERATIONS or above, an exception will be thrown instead."
        )
        operation()
    }
}