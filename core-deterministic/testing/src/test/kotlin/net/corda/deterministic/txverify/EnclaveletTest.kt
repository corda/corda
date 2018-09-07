package net.corda.deterministic.txverify

import net.corda.deterministic.bytesOfResource
import net.corda.deterministic.common.LocalSerializationRule
import net.corda.deterministic.common.verifyInEnclave
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
