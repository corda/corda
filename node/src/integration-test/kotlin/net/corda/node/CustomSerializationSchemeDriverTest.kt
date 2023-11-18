package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import de.javakaffee.kryoserializers.ArraysAsListSerializer
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.copyBytes
import net.corda.core.messaging.startFlow
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CustomSerializationScheme
import net.corda.core.serialization.SerializationSchemeContext
import net.corda.core.serialization.internal.CustomSerializationSchemeUtils.Companion.getSchemeIdIfCustomSerializationMagic
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.internal.enclosedCordapp
import org.junit.Test
import org.objenesis.instantiator.ObjectInstantiator
import org.objenesis.strategy.InstantiatorStrategy
import org.objenesis.strategy.StdInstantiatorStrategy
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier
import java.security.PublicKey
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CustomSerializationSchemeDriverTest {

    companion object {
        private fun createWireTx(serviceHub: ServiceHub, notary: Party, key: PublicKey, schemeId: Int): WireTransaction {
            val outputState = TransactionState(
                    data = DummyContract.DummyState(),
                    contract = DummyContract::class.java.name,
                    notary = notary,
                    constraint = AlwaysAcceptAttachmentConstraint
            )
            val builder = TransactionBuilder()
                    .addOutputState(outputState)
                    .addCommand(DummyCommandData, key)
            return builder.toWireTransaction(serviceHub, schemeId)
        }
    }

    @Test(timeout = 300_000)
    fun `flow can send wire transaction serialized with custom kryo serializer`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (alice, bob) = listOf(
                    startNode(NodeParameters(providedName = ALICE_NAME)),
                    startNode(NodeParameters(providedName = BOB_NAME))
            ).transpose().getOrThrow()

            val flow = alice.rpc.startFlow(::SendFlow, bob.nodeInfo.legalIdentities.single())
            assertTrue { flow.returnValue.getOrThrow() }
        }
    }

    @Test(timeout = 300_000)
    fun `flow can write a wire transaction serialized with custom kryo serializer to the ledger`() {
        driver(DriverParameters(startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val (alice, bob) = listOf(
                startNode(NodeParameters(providedName = ALICE_NAME)),
                startNode(NodeParameters(providedName = BOB_NAME))
            ).transpose().getOrThrow()

            val flow = alice.rpc.startFlow(::WriteTxToLedgerFlow, bob.nodeInfo.legalIdentities.single(), defaultNotaryIdentity)
            val txId = flow.returnValue.getOrThrow()
            val transaction = alice.rpc.startFlow(::GetTxFromDBFlow, txId).returnValue.getOrThrow()

            for(group in transaction!!.tx.componentGroups) {
                for (item in group.components) {
                    val magic = CordaSerializationMagic(item.slice(end = SerializationFactoryImpl.magicSize).copyBytes())
                    assertEquals( KryoScheme.SCHEME_ID, getSchemeIdIfCustomSerializationMagic(magic))
                }
            }
        }
    }

    @Test(timeout = 300_000)
    fun `Component groups are lazily serialized by the CustomSerializationScheme`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val alice = startNode(NodeParameters(providedName = ALICE_NAME)).getOrThrow()
            //We don't need a real notary as we don't verify the transaction in this test.
            val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
            assertTrue { alice.rpc.startFlow(::CheckComponentGroupsFlow, dummyNotary.party).returnValue.getOrThrow() }
        }
    }

    @Test(timeout = 300_000)
    fun `Map in the serialization context can be used by lazily component group serialization`() {
        driver(DriverParameters(notarySpecs = emptyList(), startNodesInProcess = true, cordappsForAllNodes = listOf(enclosedCordapp()))) {
            val alice = startNode(NodeParameters(providedName = ALICE_NAME)).getOrThrow()
            //We don't need a real notary as we don't verify the transaction in this test.
            val dummyNotary = TestIdentity(DUMMY_NOTARY_NAME, 20)
            assertTrue { alice.rpc.startFlow(::CheckComponentGroupsWithMapFlow, dummyNotary.party).returnValue.getOrThrow() }
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class WriteTxToLedgerFlow(val counterparty: Party, val notary: Party) : FlowLogic<SecureHash>() {
        @Suspendable
        override fun call(): SecureHash {
            val wireTx = createWireTx(serviceHub, notary, counterparty.owningKey, KryoScheme.SCHEME_ID)
            val partSignedTx = signWireTx(wireTx)
            val session = initiateFlow(counterparty)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(session)))
            subFlow(FinalityFlow(fullySignedTx, setOf(session)))
            return fullySignedTx.id
        }

        fun signWireTx(wireTx: WireTransaction) : SignedTransaction {
            val signatureMetadata = SignatureMetadata(
                serviceHub.myInfo.platformVersion,
                Crypto.findSignatureScheme(serviceHub.myInfo.legalIdentitiesAndCerts.first().owningKey).schemeNumberID
            )
            val signableData = SignableData(wireTx.id, signatureMetadata)
            val sig = serviceHub.keyManagementService.sign(signableData, serviceHub.myInfo.legalIdentitiesAndCerts.first().owningKey)
            return SignedTransaction(wireTx, listOf(sig))
        }
    }

    @InitiatedBy(WriteTxToLedgerFlow::class)
    class SignWireTxFlow(private val session: FlowSession): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(session) {
                override fun checkTransaction(stx: SignedTransaction) {
                    return
                }
            }
            val txId = subFlow(signTransactionFlow).id
            return subFlow(ReceiveFinalityFlow(session, expectedTxId = txId))
        }
    }

    @StartableByRPC
    class GetTxFromDBFlow(private val txId: SecureHash): FlowLogic<SignedTransaction?>() {
        override fun call(): SignedTransaction? {
            return serviceHub.validatedTransactions.getTransaction(txId)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class CheckComponentGroupsFlow(val notary: Party) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val wtx = createWireTx(serviceHub, notary, notary.owningKey, KryoScheme.SCHEME_ID)
            var success = true
            for (group in wtx.componentGroups) {
                //Component groups are lazily serialized as we iterate through.
                for (item in group.components) {
                    val magic = CordaSerializationMagic(item.slice(end = SerializationFactoryImpl.magicSize).copyBytes())
                    success = success && (getSchemeIdIfCustomSerializationMagic(magic) == KryoScheme.SCHEME_ID)
                }
            }
            return success
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class CheckComponentGroupsWithMapFlow(val notary: Party) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val outputState = TransactionState(
                    data = DummyContract.DummyState(),
                    contract = DummyContract::class.java.name,
                    notary = notary,
                    constraint = AlwaysAcceptAttachmentConstraint
            )
            val builder = TransactionBuilder()
                    .addOutputState(outputState)
                    .addCommand(DummyCommandData, notary.owningKey)
            val mapToCheckWhenSerializing = mapOf<Any, Any>(Pair(KryoSchemeWithMap.KEY, KryoSchemeWithMap.VALUE))
            val wtx = builder.toWireTransaction(serviceHub, KryoSchemeWithMap.SCHEME_ID, mapToCheckWhenSerializing)
            var success = true
            for (group in wtx.componentGroups) {
                //Component groups are lazily serialized as we iterate through.
                for (item in group.components) {
                    val magic = CordaSerializationMagic(item.slice(end = SerializationFactoryImpl.magicSize).copyBytes())
                    success = success && (getSchemeIdIfCustomSerializationMagic(magic) == KryoSchemeWithMap.SCHEME_ID)
                }
            }
            return success
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class SendFlow(val counterparty: Party) : FlowLogic<Boolean>() {
        @Suspendable
        override fun call(): Boolean {
            val wtx = createWireTx(serviceHub, counterparty, counterparty.owningKey, KryoScheme.SCHEME_ID)
            val session = initiateFlow(counterparty)
            session.send(wtx)
            return session.receive<Boolean>().unwrap {it}
        }
    }

    @StartableByRPC
    class CreateWireTxFlow(val counterparty: Party) : FlowLogic<WireTransaction>() {
        @Suspendable
        override fun call(): WireTransaction {
            return createWireTx(serviceHub, counterparty, counterparty.owningKey, KryoScheme.SCHEME_ID)
        }
    }

    @InitiatedBy(SendFlow::class)
    class ReceiveFlow(private val session: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val message = session.receive<WireTransaction>().unwrap {it}
            message.toLedgerTransaction(serviceHub)
            session.send(true)
        }
    }

    class DummyContract: Contract {
        @BelongsToContract(DummyContract::class)
        class DummyState(override val participants: List<AbstractParty> = listOf()) : ContractState
        override fun verify(tx: LedgerTransaction) {
            return
        }
    }

    object DummyCommandData : TypeOnlyCommandData()

    open class KryoScheme : CustomSerializationScheme {

        companion object {
            const val SCHEME_ID = 7
        }

        override fun getSchemeId(): Int {
            return SCHEME_ID
        }

        override fun <T : Any> deserialize(bytes: ByteSequence, clazz: Class<T>, context: SerializationSchemeContext): T {
            val kryo = Kryo()
            customiseKryo(kryo, context.deserializationClassLoader)

            val obj = Input(bytes.open()).use {
                kryo.readClassAndObject(it)
            }
            @Suppress("UNCHECKED_CAST")
            return obj as T
        }

        override fun <T : Any> serialize(obj: T, context: SerializationSchemeContext): ByteSequence {
            val kryo = Kryo()
            customiseKryo(kryo, context.deserializationClassLoader)

            val outputStream = ByteArrayOutputStream()
            Output(outputStream).use {
                kryo.writeClassAndObject(it, obj)
            }
            return ByteSequence.of(outputStream.toByteArray())
        }

        private fun customiseKryo(kryo: Kryo, classLoader: ClassLoader) {
            kryo.instantiatorStrategy = CustomInstantiatorStrategy()
            kryo.classLoader = classLoader
            kryo.register(Arrays.asList("").javaClass, ArraysAsListSerializer())
        }

        //Stolen from DefaultKryoCustomizer.kt
        private class CustomInstantiatorStrategy : InstantiatorStrategy {
            private val fallbackStrategy = StdInstantiatorStrategy()

            // Use this to allow construction of objects using a JVM backdoor that skips invoking the constructors, if there
            // is no no-arg constructor available.
            private val defaultStrategy = Kryo.DefaultInstantiatorStrategy(fallbackStrategy)

            override fun <T> newInstantiatorOf(type: Class<T>): ObjectInstantiator<T> {
                // However this doesn't work for non-public classes in the java. namespace
                val strat = if (type.name.startsWith("java.") && !Modifier.isPublic(type.modifiers)) fallbackStrategy else defaultStrategy
                return strat.newInstantiatorOf(type)
            }
        }
    }

    class KryoSchemeWithMap : KryoScheme() {

        companion object {
            const val SCHEME_ID = 8
            const val KEY = "Key"
            const val VALUE = "Value"
        }

        override fun getSchemeId(): Int {
            return SCHEME_ID
        }

        override fun <T : Any> serialize(obj: T, context: SerializationSchemeContext): ByteSequence {
            assertEquals(VALUE, context.properties[KEY])
            return super.serialize(obj, context)
        }

    }
}
