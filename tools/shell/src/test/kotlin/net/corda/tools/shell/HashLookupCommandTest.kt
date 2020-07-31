package net.corda.tools.shell

import net.corda.core.crypto.SecureHash
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

        private fun ops(txId: SecureHash): CordaRPCOps? {
            val snapshot: List<StateMachineTransactionMapping> = listOf(
                    StateMachineTransactionMapping(StateMachineRunId(UUID.randomUUID()), txId)
            )
            return Mockito.mock(CordaRPCOps::class.java).apply {
                Mockito.`when`(stateMachineRecordedTransactionMappingSnapshot()).thenReturn(snapshot)
            }
        }
    }

    @Test(timeout=300_000)
	fun `hash lookup command returns correct response`() {
        val txIdHash = DEFAULT_TXID.toString()
        val ops = ops(DEFAULT_TXID)
        val arrayWriter = CharArrayWriter()
        val response = PrintWriter(arrayWriter).use {
            HashLookupShellCommand.hashLookup(it, ops, txIdHash)
            it.flush()
            arrayWriter.toString()
        }

        MatcherAssert.assertThat(response, StringContains.containsString("Found a matching transaction with Id: $txIdHash"))
    }

    @Test(timeout=300_000)
    fun `should reject invalid txid`() {
        val ops = ops(DEFAULT_TXID)
        assertFailsWith<IllegalArgumentException>("The provided string is not a valid hexadecimal SHA-256 hash value") {
            PrintWriter(CharArrayWriter()).use {
                HashLookupShellCommand.hashLookup(it, ops, "abcdefgh")
            }
        }
    }

    @Test(timeout=300_000)
    fun `should reject unknown txid`() {
        val ops = ops(DEFAULT_TXID)
        assertFailsWith<IllegalArgumentException>("No matching transaction found") {
            PrintWriter(CharArrayWriter()).use {
                HashLookupShellCommand.hashLookup(it, ops, SecureHash.randomSHA256().toString())
            }
        }
    }
}