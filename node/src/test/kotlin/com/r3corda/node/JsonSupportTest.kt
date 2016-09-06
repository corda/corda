package com.r3corda.node

import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import com.r3corda.core.testing.PublicKeyGenerator
import com.r3corda.node.utilities.JsonSupport
import com.r3corda.testing.node.MockIdentityService
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.junit.runner.RunWith
import java.security.PublicKey
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class JsonSupportTest {

    companion object {
        val mapper = JsonSupport.createDefaultMapper(MockIdentityService(mutableListOf()))
    }

    @Property
    fun publicKeySerializingWorks(@From(PublicKeyGenerator::class) publicKey: PublicKey) {
        val serialized = mapper.writeValueAsString(publicKey)
        val parsedKey = mapper.readValue(serialized, EdDSAPublicKey::class.java)
        assertEquals(publicKey, parsedKey)
    }
}
