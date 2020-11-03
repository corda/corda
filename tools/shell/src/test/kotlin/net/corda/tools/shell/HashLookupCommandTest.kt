package net.corda.tools.shell

import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineTransactionMapping
import org.hamcrest.MatcherAssert
import org.hamcrest.core.StringContains
import org.junit.Test
import org.mockito.Mockito
import java.io.CharArrayWriter
import java.io.PrintWriter
import java.util.UUID
import kotlin.test.assertFailsWith

class HashLookupCommandTest {
    companion object {
        private val DEFAULT_TXID: SecureHash = SecureHash.randomSHA256()

        private fun ops(vararg txIds: SecureHash): CordaRPCOps? {
            val snapshot: List<StateMachineTransactionMapping> = txIds.map { txId ->
                StateMachineTransactionMapping(StateMachineRunId(UUID.randomUUID()), txId)
            }
            return Mockito.mock(CordaRPCOps::class.java).apply {
                Mockito.`when`(stateMachineRecordedTransactionMappingSnapshot()).thenReturn(snapshot)
            }
        }

        private fun runCommand(ops: CordaRPCOps?, txIdHash: String): String {
            val arrayWriter = CharArrayWriter()
            return PrintWriter(arrayWriter).use {
                HashLookupShellCommand.hashLookup(it, ops, txIdHash)
                it.flush()
                arrayWriter.toString()
            }
        }
    }

    @Test(timeout=300_000)
	fun `hash lookup command returns correct response`() {
        val ops = ops(DEFAULT_TXID)
        var response = runCommand(ops, DEFAULT_TXID.toString())

        MatcherAssert.assertThat(response, StringContains.containsString("Found a matching transaction with Id: $DEFAULT_TXID"))

        // Verify the hash of the TX ID also works
        response = runCommand(ops, DEFAULT_TXID.sha256().toString())
        MatcherAssert.assertThat(response, StringContains.containsString("Found a matching transaction with Id: $DEFAULT_TXID"))
    }

    @Test(timeout=300_000)
    fun `should reject invalid txid`() {
        val ops = ops(DEFAULT_TXID)
        assertFailsWith<IllegalArgumentException>("The provided string is not a valid hexadecimal SHA-256 hash value") {
            runCommand(ops, "abcdefgh")
        }
    }

    @Test(timeout=300_000)
    fun `should reject unknown txid`() {
        val ops = ops(DEFAULT_TXID)
        assertFailsWith<IllegalArgumentException>("No matching transaction found") {
            runCommand(ops, SecureHash.randomSHA256().toString())
        }
    }
}