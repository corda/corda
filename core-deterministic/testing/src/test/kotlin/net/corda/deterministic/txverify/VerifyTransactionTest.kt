package net.corda.deterministic.txverify

import net.corda.deterministic.bytesOfResource
import net.corda.deterministic.verifier.LocalSerializationRule
import net.corda.deterministic.verifier.verifyTransaction
import net.corda.finance.contracts.asset.Cash.Commands.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test
import kotlin.test.assertFailsWith

class VerifyTransactionTest {
    companion object {
        @ClassRule
        @JvmField
        val serialization = LocalSerializationRule(VerifyTransactionTest::class)
    }

    @Test
    fun success() {
        verifyTransaction(bytesOfResource("txverify/tx-success.bin"))
    }

    @Test
    fun failure() {
        val e = assertFailsWith<Exception> { verifyTransaction(bytesOfResource("txverify/tx-failure.bin")) }
        assertThat(e).hasMessageContaining("Required ${Move::class.java.canonicalName} command")
    }
}
