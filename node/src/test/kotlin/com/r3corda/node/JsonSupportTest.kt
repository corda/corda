package com.r3corda.node

import com.r3corda.core.node.services.testing.MockIdentityService
import com.r3corda.core.testing.PublicKeyGenerator
import com.r3corda.node.utilities.JsonSupport
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.junit.runner.RunWith
import java.security.PublicKey
import kotlin.test.assertEquals

@RunWith(com.pholser.junit.quickcheck.runner.JUnitQuickcheck::class)
class JsonSupportTest {

    companion object {
        val mapper = JsonSupport.createDefaultMapper(MockIdentityService(mutableListOf()))
    }

    @com.pholser.junit.quickcheck.Property
    fun publicKeySerializingWorks(@com.pholser.junit.quickcheck.From(PublicKeyGenerator::class) publicKey: PublicKey) {
        val serialized = mapper.writeValueAsString(publicKey)
        val parsedKey = mapper.readValue(serialized, EdDSAPublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }
}
