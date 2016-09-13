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
import com.r3corda.core.testing.PartyGenerator
import com.r3corda.protocols.BroadcastTransactionProtocol
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class BroadcastTransactionProtocolTest {

    class NotifyTxRequestMessageGenerator : Generator<BroadcastTransactionProtocol.NotifyTxRequestMessage>(BroadcastTransactionProtocol.NotifyTxRequestMessage::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus): BroadcastTransactionProtocol.NotifyTxRequestMessage {
            return BroadcastTransactionProtocol.NotifyTxRequestMessage(
                    tx = SignedTransactionGenerator().generate(random, status),
                    events = setOf(),
                    replyToParty = PartyGenerator().generate(random, status),
                    sendSessionID = random.nextLong(),
                    receiveSessionID = random.nextLong()
            )
        }
    }

    @Property
    fun serialiseDeserialiseOfNotifyMessageWorks(@From(NotifyTxRequestMessageGenerator::class) message: BroadcastTransactionProtocol.NotifyTxRequestMessage) {
        val kryo = createKryo()
        val serialized = message.serialize().bits
        val deserialized = kryo.readClassAndObject(Input(serialized))
        assertEquals(deserialized, message)
    }
}
