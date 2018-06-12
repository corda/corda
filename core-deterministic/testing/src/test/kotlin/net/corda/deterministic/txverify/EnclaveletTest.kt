@file:JvmName("Enclavelet")
package net.corda.deterministic.txverify

import net.corda.core.serialization.deserialize
import net.corda.core.transactions.LedgerTransaction
import net.corda.deterministic.bytesOfResource
import net.corda.deterministic.common.LocalSerializationRule
import net.corda.deterministic.common.TransactionVerificationRequest
import net.corda.finance.contracts.asset.Cash.Commands.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertFailsWith

class EnclaveletTest {
    companion object {
        @ClassRule
        @JvmField
        val serialization = LocalSerializationRule(EnclaveletTest::class)
    }

    @Test
    fun success() {
        verifyInEnclave(bytesOfResource("txverify/tx-success.bin"))
    }

    @Test
    fun failure() {
        val e = assertFailsWith<Exception> { verifyInEnclave(bytesOfResource("txverify/tx-failure.bin")) }
        assertThat(e).hasMessageContaining("Required ${Move::class.java.canonicalName} command")
    }
}

/**
 * Returns either null to indicate success when the transactions are validated, or a string with the
 * contents of the error. Invoked via JNI in response to an enclave RPC. The argument is a serialised
 * [TransactionVerificationRequest].
 *
 * Note that it is assumed the signatures were already checked outside the sandbox: the purpose of this code
 * is simply to check the sensitive, app specific parts of a transaction.
 *
 * TODO: Transaction data is meant to be encrypted under an enclave-private key.
 */
@Throws(Exception::class)
private fun verifyInEnclave(reqBytes: ByteArray) {
    deserialize(reqBytes).verify()
}

private fun deserialize(reqBytes: ByteArray): LedgerTransaction {
    return reqBytes.deserialize<TransactionVerificationRequest>()
        .toLedgerTransaction()
}
