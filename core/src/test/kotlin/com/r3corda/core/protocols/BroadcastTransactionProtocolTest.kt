package com.r3corda.core.protocols

import com.esotericsoftware.kryo.io.Input
import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Produced
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import com.r3corda.contracts.testing.signedTransactionGenerator
import com.r3corda.core.serialization.createKryo
import com.r3corda.core.serialization.serialize
import com.r3corda.core.testing.generator
import com.r3corda.core.testing.partyGenerator
import com.r3corda.protocols.BroadcastTransactionProtocol
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class BroadcastTransactionProtocolTest {

    companion object {
        val notifyTxRequestMessageGenerator = generator { random, status ->
            BroadcastTransactionProtocol.NotifyTxRequestMessage(
                    tx = signedTransactionGenerator.generate(random, status),
                    events = setOf(),
                    replyToParty = partyGenerator.generate(random, status),
                    sessionID = random.nextLong()
            )
        }
        class NotifyTxRequestMessageGenerator: Generator<BroadcastTransactionProtocol.NotifyTxRequestMessage>(BroadcastTransactionProtocol.NotifyTxRequestMessage::class.java) {
            override fun generate(random: SourceOfRandomness, status: GenerationStatus) = notifyTxRequestMessageGenerator.generate(random, status)
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
