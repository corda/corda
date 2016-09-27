package com.r3corda.core.protocols

import com.esotericsoftware.kryo.io.Input
import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import com.r3corda.contracts.testing.SignedTransactionGenerator
import com.r3corda.core.serialization.createKryo
import com.r3corda.core.serialization.serialize
import com.r3corda.protocols.BroadcastTransactionProtocol.NotifyTxRequest
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class BroadcastTransactionProtocolTest {

    class NotifyTxRequestMessageGenerator : Generator<NotifyTxRequest>(NotifyTxRequest::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus): NotifyTxRequest {
            return NotifyTxRequest(tx = SignedTransactionGenerator().generate(random, status), events = setOf())
        }
    }

    @Property
    fun serialiseDeserialiseOfNotifyMessageWorks(@From(NotifyTxRequestMessageGenerator::class) message: NotifyTxRequest) {
        val kryo = createKryo()
        val serialized = message.serialize().bits
        val deserialized = kryo.readClassAndObject(Input(serialized))
        assertEquals(deserialized, message)
    }
}
