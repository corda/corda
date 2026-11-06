package net.corda.nodeapi.internal.persistence

import liquibase.exception.CommandExecutionException
import liquibase.exception.LiquibaseException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class LiquibaseExceptionUnwrapTest {
    // The chain Liquibase 4.33 throws when a change set fails: CommandExecutionException -> LiquibaseException -> MigrationFailedException.
    private val asThrownByLiquibase420 = LiquibaseException(IllegalStateException("Migration failed for changeset x"))

    @Test(timeout = 300_000)
    fun `the command wrapper added by Liquibase 4_33 is removed, so the message is the one Liquibase 4_20 gave`() {
        val asThrownByLiquibase433 = CommandExecutionException(asThrownByLiquibase420)
        val reported = asThrownByLiquibase433.withoutCommandWrapper()
        assertSame(asThrownByLiquibase420, reported)
        assertEquals("java.lang.IllegalStateException: Migration failed for changeset x", reported.message)
    }

    @Test(timeout = 300_000)
    fun `other exceptions are reported as they are`() {
        assertSame(asThrownByLiquibase420, asThrownByLiquibase420.withoutCommandWrapper())
        val commandFailure = CommandExecutionException("no wrapped Liquibase exception")
        assertSame(commandFailure, commandFailure.withoutCommandWrapper())
    }
}
