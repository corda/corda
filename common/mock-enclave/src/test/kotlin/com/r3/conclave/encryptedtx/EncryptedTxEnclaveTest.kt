package com.r3.conclave.encryptedtx

import com.github.benmanes.caffeine.cache.Caffeine
import com.r3.conclave.encryptedtx.dto.ConclaveLedgerTxModel
import com.r3.conclave.encryptedtx.dto.VerifiableTxAndDependencies
import com.r3.conclave.encryptedtx.enclave.EncryptedTxEnclave
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.serialize
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.SerializationFactoryCacheKey
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.testing.core.*
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.internal.FINANCE_CORDAPPS
import org.junit.After
import org.junit.Before
import org.junit.Test

class EncryptedTxEnclaveTest {

    private lateinit var mockNet: MockNetwork
    private val initialBalance = 2000.DOLLARS
    private val ref = OpaqueBytes.of(0x01)

    private lateinit var bankOfCordaNode: StartedMockNode
    private lateinit var bankOfCorda: Party

    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode

    private val serializerFactoriesForContexts = Caffeine.newBuilder()
            .maximumSize(128)
            .build<SerializationFactoryCacheKey, SerializerFactory>()
            .asMap()

    private lateinit var serializationFactoryImpl: SerializationFactoryImpl

    private val encryptedTxEnclave = EncryptedTxEnclave()

    @Before
    fun start() {
        mockNet = MockNetwork(MockNetworkParameters(servicePeerAllocationStrategy = RoundRobin(), cordappsForAllNodes = FINANCE_CORDAPPS))
        bankOfCordaNode = mockNet.createPartyNode(BOC_NAME)
        bankOfCorda = bankOfCordaNode.info.identityFromX500Name(BOC_NAME)

        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)

        val serverScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)
        val clientScheme = AMQPServerSerializationScheme(emptyList(), serializerFactoriesForContexts)

        serializationFactoryImpl = SerializationFactoryImpl().apply {
            registerScheme(serverScheme)
            registerScheme(clientScheme)
        }
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test(timeout=300_000)
    fun `pay some cash`() {
        val payTo = aliceNode.info.singleIdentity()
        val expectedPayment = 500.DOLLARS

        var future = bankOfCordaNode.startFlow(CashIssueFlow(initialBalance, ref, mockNet.defaultNotaryIdentity))
        val issuanceStx = future.getOrThrow().stx

        val issuanceConclaveLedgerTxBytes = issuanceStx
                .toLedgerTxModel(bankOfCordaNode.services)
                .serialize(serializationFactoryImpl)
                .bytes

        val encryptedTx = encryptedTxEnclave.encryptSignedTx(issuanceConclaveLedgerTxBytes)

        future = bankOfCordaNode.startFlow(CashPaymentFlow(expectedPayment, payTo))
        mockNet.runNetwork()
        val paymentStx = future.getOrThrow().stx

        val ledgerTxModel = paymentStx.toLedgerTxModel(bankOfCordaNode.services)
        val txAndDependenciesBytes = VerifiableTxAndDependencies(ledgerTxModel, setOf(encryptedTx)).serialize()

        encryptedTxEnclave.verifyTx(txAndDependenciesBytes.bytes)
    }

    private fun SignedTransaction.toLedgerTxModel(services: ServiceHub): ConclaveLedgerTxModel {
        val ledgerTx = this.toLedgerTransaction(services)

        return ConclaveLedgerTxModel(
                signedTransaction = this,
                inputStates = ledgerTx.inputs.toTypedArray(),
                attachments = ledgerTx.attachments.toTypedArray(),
                networkParameters = ledgerTx.networkParameters!!,
                references = ledgerTx.references.toTypedArray()
        )
    }
}
