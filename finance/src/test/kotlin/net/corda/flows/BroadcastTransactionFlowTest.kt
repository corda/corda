package net.corda.flows

import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import net.corda.contracts.testing.SignedTransactionGenerator
import net.corda.core.flows.BroadcastTransactionFlow.NotifyTxRequest
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class BroadcastTransactionFlowTest {

    class NotifyTxRequestMessageGenerator : Generator<NotifyTxRequest>(NotifyTxRequest::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus): NotifyTxRequest {
            return NotifyTxRequest(tx = SignedTransactionGenerator().generate(random, status))
        }
    }

    @Property
    fun serialiseDeserialiseOfNotifyMessageWorks(@From(NotifyTxRequestMessageGenerator::class) message: NotifyTxRequest) {
        val serialized = message.serialize().bytes
        val deserialized = serialized.deserialize<NotifyTxRequest>()
        assertEquals(deserialized, message)
    }
}
