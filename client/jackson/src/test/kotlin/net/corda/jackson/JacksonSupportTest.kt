package net.corda.jackson

import com.fasterxml.jackson.databind.SerializationFeature
import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import net.corda.core.contracts.Amount
import net.corda.core.contracts.USD
import net.corda.core.testing.PublicKeyGenerator
import net.corda.testing.TestDependencyInjectionBase
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.junit.Test
import org.junit.runner.RunWith
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class JacksonSupportTest : TestDependencyInjectionBase() {
    companion object {
        val mapper = JacksonSupport.createNonRpcMapper()
    }

    @Property
    fun publicKeySerializingWorks(@From(PublicKeyGenerator::class) publicKey: PublicKey) {
        val serialized = mapper.writeValueAsString(publicKey)
        val parsedKey = mapper.readValue(serialized, EdDSAPublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }

    private class Dummy(val notional: Amount<Currency>)

    @Test
    fun readAmount() {
        val oldJson = """
            {
              "notional": {
                  "quantity": 2500000000,
                  "token": "USD"
              }
            }
        """
        val newJson = """ { "notional" : "$25000000" } """

        assertEquals(Amount(2500000000L, USD), mapper.readValue(newJson, Dummy::class.java).notional)
        assertEquals(Amount(2500000000L, USD), mapper.readValue(oldJson, Dummy::class.java).notional)
    }

    @Test
    fun writeAmount() {
        val writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT)
        assertEquals("""{"notional":"25000000.00 USD"}""", writer.writeValueAsString(Dummy(Amount.parseCurrency("$25000000"))))
    }
}
