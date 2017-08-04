package net.corda.flows

import com.pholser.junit.quickcheck.From
import com.pholser.junit.quickcheck.Property
import com.pholser.junit.quickcheck.generator.GenerationStatus
import com.pholser.junit.quickcheck.generator.Generator
import com.pholser.junit.quickcheck.random.SourceOfRandomness
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck
import net.corda.contracts.testing.SignedTransactionGenerator
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.initialiseTestSerialization
import net.corda.testing.resetTestSerialization
import org.junit.After
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(JUnitQuickcheck::class)
class BroadcastTransactionFlowTest {

    class NotifyTxRequestMessageGenerator : Generator<SignedTransaction>(SignedTransaction::class.java) {
        override fun generate(random: SourceOfRandomness, status: GenerationStatus): SignedTransaction {
            initialiseTestSerialization()
            return SignedTransactionGenerator().generate(random, status)
        }
    }

    @After
    fun teardown() {
        resetTestSerialization()
    }

    @Property
    fun serialiseDeserialiseOfNotifyMessageWorks(@From(NotifyTxRequestMessageGenerator::class) message: SignedTransaction) {
        val serialized = message.serialize().bytes
        val deserialized = serialized.deserialize<SignedTransaction>()
        assertEquals(deserialized, message)
    }
}
