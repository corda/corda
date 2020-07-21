package net.corda.commmon.logging.errorReporting

import net.corda.common.logging.errorReporting.NodeDatabaseErrors
import java.net.InetAddress

class DatabaseErrorsTest : ErrorCodeTest<NodeDatabaseErrors>(NodeDatabaseErrors::class.java) {
    override val dataForCodes = mapOf(
            NodeDatabaseErrors.COULD_NOT_CONNECT to listOf<Any>(),
            NodeDatabaseErrors.FAILED_STARTUP to listOf("This is a test message"),
            NodeDatabaseErrors.MISSING_DRIVER to listOf(),
            NodeDatabaseErrors.PASSWORD_REQUIRED_FOR_H2 to listOf(InetAddress.getLocalHost())
    )
}