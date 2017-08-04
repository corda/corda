package net.corda.node.messaging

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.CommercialPaper
import net.corda.core.concurrent.CordaFuture
import net.corda.contracts.asset.CASH
import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.`issued by`
import net.corda.contracts.asset.`owned by`
import net.corda.core.contracts.*
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.AnonymousPartyAndPath
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.concurrent.map
import net.corda.core.internal.rootCause
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ServiceInfo
import net.corda.core.node.services.Vault
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.days
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.toNonEmptySet
import net.corda.core.utilities.unwrap
import net.corda.flows.TwoPartyTradeFlow.Buyer
import net.corda.flows.TwoPartyTradeFlow.Seller
import net.corda.node.internal.AbstractNode
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.checkpoints
import net.corda.node.utilities.CordaPersistence
import net.corda.testing.*
import net.corda.testing.contracts.fillWithSomeTestCash
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.util.*
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * In this example, Alice wishes to sell her commercial paper to Bob in return for $1,000,000 and they wish to do
 * it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Alice and Bob already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeFlowTests {
    lateinit var mockNet: MockNetwork

    @Before
    fun before() {
        LogHelper.setLevel("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @After
    fun after() {
        mockNet.stopNodes()
        LogHelper.reset("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @Test
    fun `trade cash for commercial paper`() {
        // We run this in parallel threads to help catch any race conditions that may exist. The other tests
        // we run in the unit test thread exclusively to speed things up, ensure deterministic results and
        // allow interruption half way through.
        mockNet = MockNetwork(false, true)

        ledger(initialiseSerialization = false) {
            val basketOfNodes = mockNet.createSomeNodes(3)
            val notaryNode = basketOfNodes.notaryNode
            val aliceNode = basketOfNodes.partyNodes[0]
            val bobNode = basketOfNodes.partyNodes[1]
            val bankNode = basketOfNodes.partyNodes[2]
            val cashIssuer = bankNode.info.legalIdentity.ref(1)
            val cpIssuer = bankNode.info.legalIdentity.ref(1, 2, 3)

            aliceNode.disableDBCloseOnStop()
            bobNode.disableDBCloseOnStop()

            bobNode.database.transaction {
                bobNode.services.fillWithSomeTestCash(2000.DOLLARS, bankNode.services, outputNotary = notaryNode.info.notaryIdentity,
                        issuedBy = cashIssuer)
            }

            val alicesFakePaper = aliceNode.database.transaction {
                fillUpForSeller(false, cpIssuer, aliceNode.info.legalIdentity,
                        1200.DOLLARS `issued by` bankNode.info.legalIdentity.ref(0), null, notaryNode.info.notaryIdentity).second
            }

            insertFakeTransactions(alicesFakePaper, aliceNode, notaryNode, bankNode)

            val (bobStateMachine, aliceResult) = runBuyerAndSeller(notaryNode, aliceNode, bobNode,
                    "alice's paper".outputStateAndRef())

            // TODO: Verify that the result was inserted into the transaction database.
            // assertEquals(bobResult.get(), aliceNode.storage.validatedTransactions[aliceResult.get().id])
            assertEquals(aliceResult.getOrThrow(), bobStateMachine.getOrThrow().resultFuture.getOrThrow())

            aliceNode.stop()
            bobNode.stop()

            aliceNode.database.transaction {
                assertThat(aliceNode.checkpointStorage.checkpoints()).isEmpty()
            }
            aliceNode.manuallyCloseDB()
            bobNode.database.transaction {
                assertThat(bobNode.checkpointStorage.checkpoints()).isEmpty()
            }
            bobNode.manuallyCloseDB()
        }
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `trade cash for commercial paper fails using soft locking`() {
        mockNet = MockNetwork(false, true)

        ledger(initialiseSerialization = false) {
            val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
            val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
            val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
            val bankNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOC.name)
            val cashIssuer = bankNode.info.legalIdentity.ref(1)
            val cpIssuer = bankNode.info.legalIdentity.ref(1, 2, 3)

            aliceNode.disableDBCloseOnStop()
            bobNode.disableDBCloseOnStop()

            val cashStates = bobNode.database.transaction {
                bobNode.services.fillWithSomeTestCash(2000.DOLLARS, bankNode.services, notaryNode.info.notaryIdentity, 3, 3,
                            issuedBy = cashIssuer)
                }

            val alicesFakePaper = aliceNode.database.transaction {
                fillUpForSeller(false, cpIssuer, aliceNode.info.legalIdentity,
                        1200.DOLLARS `issued by` bankNode.info.legalIdentity.ref(0), null, notaryNode.info.notaryIdentity).second
            }

            insertFakeTransactions(alicesFakePaper, aliceNode, notaryNode, bankNode)

            val cashLockId = UUID.randomUUID()
            bobNode.database.transaction {
                // lock the cash states with an arbitrary lockId (to prevent the Buyer flow from claiming the states)
                val refs = cashStates.states.map { it.ref }
                if (refs.isNotEmpty()) {
                    bobNode.services.vaultService.softLockReserve(cashLockId, refs.toNonEmptySet())
                }
            }

            val (bobStateMachine, aliceResult) = runBuyerAndSeller(notaryNode, aliceNode, bobNode,
                    "alice's paper".outputStateAndRef())

            assertEquals(aliceResult.getOrThrow(), bobStateMachine.getOrThrow().resultFuture.getOrThrow())

            aliceNode.stop()
            bobNode.stop()

            aliceNode.database.transaction {
                assertThat(aliceNode.checkpointStorage.checkpoints()).isEmpty()
            }
            aliceNode.manuallyCloseDB()
            bobNode.database.transaction {
                assertThat(bobNode.checkpointStorage.checkpoints()).isEmpty()
            }
            bobNode.manuallyCloseDB()
        }
    }

    @Test
    fun `shutdown and restore`() {
        mockNet = MockNetwork(false)
        ledger(initialiseSerialization = false) {
            val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
            val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
            var bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
            val bankNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOC.name)
            val cashIssuer = bankNode.info.legalIdentity.ref(1)
            val cpIssuer = bankNode.info.legalIdentity.ref(1, 2, 3)

            aliceNode.services.identityService.registerIdentity(bobNode.info.legalIdentityAndCert)
            bobNode.services.identityService.registerIdentity(aliceNode.info.legalIdentityAndCert)
            aliceNode.disableDBCloseOnStop()
            bobNode.disableDBCloseOnStop()

            val bobAddr = bobNode.network.myAddress as InMemoryMessagingNetwork.PeerHandle
            val networkMapAddress = notaryNode.network.myAddress

            mockNet.runNetwork() // Clear network map registration messages

            bobNode.database.transaction {
                bobNode.services.fillWithSomeTestCash(2000.DOLLARS, bankNode.services, outputNotary = notaryNode.info.notaryIdentity,
                        issuedBy = cashIssuer)
            }
            val alicesFakePaper = aliceNode.database.transaction {
                fillUpForSeller(false, cpIssuer, aliceNode.info.legalIdentity,
                        1200.DOLLARS `issued by` bankNode.info.legalIdentity.ref(0), null, notaryNode.info.notaryIdentity).second
            }
            insertFakeTransactions(alicesFakePaper, aliceNode, notaryNode, bankNode)
            val aliceFuture = runBuyerAndSeller(notaryNode, aliceNode, bobNode, "alice's paper".outputStateAndRef()).sellerResult

            // Everything is on this thread so we can now step through the flow one step at a time.
            // Seller Alice already sent a message to Buyer Bob. Pump once:
            bobNode.pumpReceive()

            // Bob sends a couple of queries for the dependencies back to Alice. Alice reponds.
            aliceNode.pumpReceive()
            bobNode.pumpReceive()
            aliceNode.pumpReceive()
            bobNode.pumpReceive()
            aliceNode.pumpReceive()
            bobNode.pumpReceive()

            // OK, now Bob has sent the partial transaction back to Alice and is waiting for Alice's signature.
            bobNode.database.transaction {
                assertThat(bobNode.checkpointStorage.checkpoints()).hasSize(1)
            }

            val storage = bobNode.services.validatedTransactions
            val bobTransactionsBeforeCrash = bobNode.database.transaction {
                (storage as DBTransactionStorage).transactions
            }
            assertThat(bobTransactionsBeforeCrash).isNotEmpty

            // .. and let's imagine that Bob's computer has a power cut. He now has nothing now beyond what was on disk.
            bobNode.stop()

            // Alice doesn't know that and carries on: she wants to know about the cash transactions he's trying to use.
            // She will wait around until Bob comes back.
            assertThat(aliceNode.pumpReceive()).isNotNull()

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Bob was waiting on before the reboot occurred.
            bobNode = mockNet.createNode(networkMapAddress, bobAddr.id, object : MockNetwork.Factory<MockNetwork.MockNode> {
                override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                    advertisedServices: Set<ServiceInfo>, id: Int, overrideServices: Map<ServiceInfo, KeyPair>?,
                                    entropyRoot: BigInteger): MockNetwork.MockNode {
                    return MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, bobAddr.id, overrideServices, entropyRoot)
                }
            }, true, BOB.name)

            // Find the future representing the result of this state machine again.
            val bobFuture = bobNode.smm.findStateMachines(BuyerAcceptor::class.java).single().second

            // And off we go again.
            mockNet.runNetwork()

            // Bob is now finished and has the same transaction as Alice.
            assertThat(bobFuture.getOrThrow()).isEqualTo(aliceFuture.getOrThrow())

            assertThat(bobNode.smm.findStateMachines(Buyer::class.java)).isEmpty()
            bobNode.database.transaction {
                assertThat(bobNode.checkpointStorage.checkpoints()).isEmpty()
            }
            aliceNode.database.transaction {
                assertThat(aliceNode.checkpointStorage.checkpoints()).isEmpty()
            }

            bobNode.database.transaction {
                val restoredBobTransactions = bobTransactionsBeforeCrash.filter {
                    bobNode.services.validatedTransactions.getTransaction(it.id) != null
                }
                assertThat(restoredBobTransactions).containsAll(bobTransactionsBeforeCrash)
            }

            aliceNode.manuallyCloseDB()
            bobNode.manuallyCloseDB()
        }
    }

    // Creates a mock node with an overridden storage service that uses a RecordingMap, that lets us test the order
    // of gets and puts.
    private fun makeNodeWithTracking(
            networkMapAddress: SingleMessageRecipient?,
            name: X500Name): MockNetwork.MockNode {
        // Create a node in the mock network ...
        return mockNet.createNode(networkMapAddress, nodeFactory = object : MockNetwork.Factory<MockNetwork.MockNode> {
            override fun create(config: NodeConfiguration,
                                network: MockNetwork,
                                networkMapAddr: SingleMessageRecipient?,
                                advertisedServices: Set<ServiceInfo>, id: Int,
                                overrideServices: Map<ServiceInfo, KeyPair>?,
                                entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                    // That constructs a recording tx storage
                    override fun makeTransactionStorage(): WritableTransactionStorage {
                        return RecordingTransactionStorage(database, super.makeTransactionStorage())
                    }
                }
            }
        }, legalName = name)
    }

    @Test
    fun `check dependencies of sale asset are resolved`() {
        mockNet = MockNetwork(false)

        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = makeNodeWithTracking(notaryNode.network.myAddress, ALICE.name)
        val bobNode = makeNodeWithTracking(notaryNode.network.myAddress, BOB.name)
        val bankNode = makeNodeWithTracking(notaryNode.network.myAddress, BOC.name)
        val issuer = bankNode.info.legalIdentity.ref(1, 2, 3)

        ledger(aliceNode.services, initialiseSerialization = false) {

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = aliceNode.database.transaction {
                attachment(ByteArrayInputStream(stream.toByteArray()))
            }

            val bobsFakeCash = fillUpForBuyer(false, issuer, AnonymousParty(bobNode.info.legalIdentity.owningKey),
                    notaryNode.info.notaryIdentity).second
            val bobsSignedTxns = insertFakeTransactions(bobsFakeCash, bobNode, notaryNode, bankNode)
            val alicesFakePaper = aliceNode.database.transaction {
                fillUpForSeller(false, issuer, aliceNode.info.legalIdentity,
                        1200.DOLLARS `issued by` bankNode.info.legalIdentity.ref(0), attachmentID, notaryNode.info.notaryIdentity).second
            }
            val alicesSignedTxns = insertFakeTransactions(alicesFakePaper, aliceNode, notaryNode, bankNode)

            mockNet.runNetwork() // Clear network map registration messages

            runBuyerAndSeller(notaryNode, aliceNode, bobNode, "alice's paper".outputStateAndRef())

            mockNet.runNetwork()

            run {
                val records = (bobNode.services.validatedTransactions as RecordingTransactionStorage).records
                // Check Bobs's database accesses as Bob's cash transactions are downloaded by Alice.
                records.expectEvents(isStrict = false) {
                    sequence(
                            // Buyer Bob is told about Alice's commercial paper, but doesn't know it ..
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // He asks and gets the tx, validates it, sees it's a self issue with no dependencies, stores.
                            expect(TxRecord.Add(alicesSignedTxns.values.first())),
                            // Alice gets Bob's proposed transaction and doesn't know his two cash states. She asks, Bob answers.
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            // Alice notices that Bob's cash txns depend on a third tx she also doesn't know. She asks, Bob answers.
                            expect(TxRecord.Get(bobsFakeCash[0].id))
                    )
                }

                // Bob has downloaded the attachment.
                bobNode.database.transaction {
                    bobNode.services.attachments.openAttachment(attachmentID)!!.openAsJAR().use {
                        it.nextJarEntry
                        val contents = it.reader().readText()
                        assertTrue(contents.contains("Our commercial paper is top notch stuff"))
                    }
                }
            }

            // And from Alice's perspective ...
            run {
                val records = (aliceNode.services.validatedTransactions as RecordingTransactionStorage).records
                records.expectEvents(isStrict = false) {
                    sequence(
                            // Seller Alice sends her seller info to Bob, who wants to check the asset for sale.
                            // He requests, Alice looks up in her DB to send the tx to Bob
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // Seller Alice gets a proposed tx which depends on Bob's two cash txns and her own tx.
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // Alice notices that Bob's cash txns depend on a third tx she also doesn't know.
                            expect(TxRecord.Get(bobsFakeCash[0].id)),
                            // Bob answers with the transactions that are now all verifiable, as Alice bottomed out.
                            // Bob's transactions are valid, so she commits to the database
                            expect(TxRecord.Add(bobsSignedTxns[bobsFakeCash[0].id]!!)),
                            expect(TxRecord.Get(bobsFakeCash[0].id)), // Verify
                            expect(TxRecord.Add(bobsSignedTxns[bobsFakeCash[2].id]!!)),
                            expect(TxRecord.Get(bobsFakeCash[0].id)), // Verify
                            expect(TxRecord.Add(bobsSignedTxns[bobsFakeCash[1].id]!!)),
                            // Now she verifies the transaction is contract-valid (not signature valid) which means
                            // looking up the states again.
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            expect(TxRecord.Get(alicesFakePaper[0].id)),
                            // Alice needs to look up the input states to find out which Notary they point to
                            expect(TxRecord.Get(bobsFakeCash[1].id)),
                            expect(TxRecord.Get(bobsFakeCash[2].id)),
                            expect(TxRecord.Get(alicesFakePaper[0].id))
                    )
                }
            }
        }
    }

    @Test
    fun `track works`() {
        mockNet = MockNetwork(false)

        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = makeNodeWithTracking(notaryNode.network.myAddress, ALICE.name)
        val bobNode = makeNodeWithTracking(notaryNode.network.myAddress, BOB.name)
        val bankNode = makeNodeWithTracking(notaryNode.network.myAddress, BOC.name)
        val issuer = bankNode.info.legalIdentity.ref(1, 2, 3)

        ledger(aliceNode.services, initialiseSerialization = false) {

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = aliceNode.database.transaction {
                attachment(ByteArrayInputStream(stream.toByteArray()))
            }

            val bobsKey = bobNode.services.keyManagementService.keys.single()
            val bobsFakeCash = fillUpForBuyer(false, issuer, AnonymousParty(bobsKey),
                    notaryNode.info.notaryIdentity).second
            insertFakeTransactions(bobsFakeCash, bobNode, notaryNode, bankNode)

            val alicesFakePaper = aliceNode.database.transaction {
                fillUpForSeller(false, issuer, aliceNode.info.legalIdentity,
                        1200.DOLLARS `issued by` bankNode.info.legalIdentity.ref(0), attachmentID, notaryNode.info.notaryIdentity).second
            }

            insertFakeTransactions(alicesFakePaper, aliceNode, notaryNode, bankNode)

            mockNet.runNetwork() // Clear network map registration messages

            val aliceTxStream = aliceNode.services.validatedTransactions.track().updates
            val aliceTxMappings = with(aliceNode) {
                database.transaction { services.stateMachineRecordedTransactionMapping.track().updates }
            }
            val aliceSmId = runBuyerAndSeller(notaryNode, aliceNode, bobNode,
                    "alice's paper".outputStateAndRef()).sellerId

            mockNet.runNetwork()

            // We need to declare this here, if we do it inside [expectEvents] kotlin throws an internal compiler error(!).
            val aliceTxExpectations = sequence(
                    expect { tx: SignedTransaction ->
                        require(tx.id == bobsFakeCash[0].id)
                    },
                    expect { tx: SignedTransaction ->
                        require(tx.id == bobsFakeCash[2].id)
                    },
                    expect { tx: SignedTransaction ->
                        require(tx.id == bobsFakeCash[1].id)
                    }
            )
            aliceTxStream.expectEvents { aliceTxExpectations }
            val aliceMappingExpectations = sequence(
                    expect<StateMachineTransactionMapping> { (stateMachineRunId, transactionId) ->
                        require(stateMachineRunId == aliceSmId)
                        require(transactionId == bobsFakeCash[0].id)
                    },
                    expect { (stateMachineRunId, transactionId) ->
                        require(stateMachineRunId == aliceSmId)
                        require(transactionId == bobsFakeCash[2].id)
                    },
                    expect { (stateMachineRunId, transactionId) ->
                        require(stateMachineRunId == aliceSmId)
                        require(transactionId == bobsFakeCash[1].id)
                    }
            )
            aliceTxMappings.expectEvents { aliceMappingExpectations }
        }
    }

    @Test
    fun `dependency with error on buyer side`() {
        mockNet = MockNetwork(false)
        ledger(initialiseSerialization = false) {
            runWithError(true, false, "at least one cash input")
        }
    }

    @Test
    fun `dependency with error on seller side`() {
        mockNet = MockNetwork(false)
        ledger(initialiseSerialization = false) {
            runWithError(false, true, "Issuances have a time-window")
        }
    }

    private data class RunResult(
            // The buyer is not created immediately, only when the seller starts running
            val buyer: CordaFuture<FlowStateMachine<*>>,
            val sellerResult: CordaFuture<SignedTransaction>,
            val sellerId: StateMachineRunId
    )

    private fun runBuyerAndSeller(notaryNode: MockNetwork.MockNode,
                                  sellerNode: MockNetwork.MockNode,
                                  buyerNode: MockNetwork.MockNode,
                                  assetToSell: StateAndRef<OwnableState>): RunResult {
        val anonymousSeller = sellerNode.services.let { serviceHub ->
            serviceHub.keyManagementService.freshKeyAndCert(serviceHub.myInfo.legalIdentityAndCert, false)
        }
        val buyerFlows: Observable<BuyerAcceptor> = buyerNode.registerInitiatedFlow(BuyerAcceptor::class.java)
        val firstBuyerFiber = buyerFlows.toFuture().map { it.stateMachine }
        val seller = SellerInitiator(buyerNode.info.legalIdentity, notaryNode.info, assetToSell, 1000.DOLLARS, anonymousSeller)
        val sellerResult = sellerNode.services.startFlow(seller).resultFuture
        return RunResult(firstBuyerFiber, sellerResult, seller.stateMachine.id)
    }

    @InitiatingFlow
    class SellerInitiator(val buyer: Party,
                          val notary: NodeInfo,
                          val assetToSell: StateAndRef<OwnableState>,
                          val price: Amount<Currency>,
                          val me: AnonymousPartyAndPath) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            send(buyer, Pair(notary.notaryIdentity, price))
            return subFlow(Seller(
                    buyer,
                    notary,
                    assetToSell,
                    price,
                    me.party))
        }
    }

    @InitiatedBy(SellerInitiator::class)
    class BuyerAcceptor(val seller: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val (notary, price) = receive<Pair<Party, Amount<Currency>>>(seller).unwrap {
                require(serviceHub.networkMapCache.isNotary(it.first)) { "${it.first} is not a notary" }
                it
            }
            return subFlow(Buyer(seller, notary, price, CommercialPaper.State::class.java))
        }
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.runWithError(
            bobError: Boolean,
            aliceError: Boolean,
            expectedMessageSubstring: String
    ) {
        val notaryNode = mockNet.createNotaryNode(null, DUMMY_NOTARY.name)
        val aliceNode = mockNet.createPartyNode(notaryNode.network.myAddress, ALICE.name)
        val bobNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOB.name)
        val bankNode = mockNet.createPartyNode(notaryNode.network.myAddress, BOC.name)
        val issuer = bankNode.info.legalIdentity.ref(1, 2, 3)

        val bobsBadCash = bobNode.database.transaction {
            fillUpForBuyer(bobError, issuer, bobNode.info.legalIdentity,
                    notaryNode.info.notaryIdentity).second
        }
        val alicesFakePaper = aliceNode.database.transaction {
            fillUpForSeller(aliceError, issuer, aliceNode.info.legalIdentity,
                    1200.DOLLARS `issued by` issuer, null, notaryNode.info.notaryIdentity).second
        }

        insertFakeTransactions(bobsBadCash, bobNode, notaryNode, bankNode)
        insertFakeTransactions(alicesFakePaper, aliceNode, notaryNode, bankNode)

        mockNet.runNetwork() // Clear network map registration messages

        val (bobStateMachine, aliceResult) = runBuyerAndSeller(notaryNode, aliceNode, bobNode, "alice's paper".outputStateAndRef())

        mockNet.runNetwork()

        val e = assertFailsWith<TransactionVerificationException> {
            if (bobError)
                aliceResult.getOrThrow()
            else
                bobStateMachine.getOrThrow().resultFuture.getOrThrow()
        }
        val underlyingMessage = e.rootCause.message!!
        if (expectedMessageSubstring !in underlyingMessage) {
            assertEquals(expectedMessageSubstring, underlyingMessage)
        }
    }


    private fun insertFakeTransactions(
            wtxToSign: List<WireTransaction>,
            node: AbstractNode,
            notaryNode: AbstractNode,
            vararg extraSigningNodes: AbstractNode): Map<SecureHash, SignedTransaction> {

        val signed = wtxToSign.map {
            val id = it.id
            val sigs = mutableListOf<TransactionSignature>()
            sigs.add(node.services.keyManagementService.sign(SignableData(id, SignatureMetadata(1, Crypto.findSignatureScheme(node.services.legalIdentityKey).schemeNumberID)), node.services.legalIdentityKey))
            sigs.add(notaryNode.services.keyManagementService.sign(SignableData(id, SignatureMetadata(1, Crypto.findSignatureScheme(notaryNode.services.notaryIdentityKey).schemeNumberID)), notaryNode.services.notaryIdentityKey))
            extraSigningNodes.forEach { currentNode ->
                sigs.add(currentNode.services.keyManagementService.sign(SignableData(id, SignatureMetadata(1, Crypto.findSignatureScheme(currentNode.info.legalIdentity.owningKey).schemeNumberID)), currentNode.info.legalIdentity.owningKey))
            }
            SignedTransaction(it, sigs)
        }
        return node.database.transaction {
            node.services.recordTransactions(signed)
            val validatedTransactions = node.services.validatedTransactions
            if (validatedTransactions is RecordingTransactionStorage) {
                validatedTransactions.records.clear()
            }
            signed.associateBy { it.id }
        }
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.fillUpForBuyer(
            withError: Boolean,
            issuer: PartyAndReference,
            owner: AbstractParty,
            notary: Party): Pair<Vault<ContractState>, List<WireTransaction>> {
        val interimOwner = issuer.party
        // Bob (Buyer) has some cash he got from the Bank of Elbonia, Alice (Seller) has some commercial paper she
        // wants to sell to Bob.
        val eb1 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            // Issued money to itself.
            output("elbonian money 1", notary = notary) { 800.DOLLARS.CASH `issued by` issuer `owned by` interimOwner }
            output("elbonian money 2", notary = notary) { 1000.DOLLARS.CASH `issued by` issuer `owned by` interimOwner }
            if (!withError) {
                command(issuer.party.owningKey) { Cash.Commands.Issue() }
            } else {
                // Put a broken command on so at least a signature is created
                command(issuer.party.owningKey) { Cash.Commands.Move() }
            }
            timeWindow(TEST_TX_TIME)
            if (withError) {
                this.fails()
            } else {
                this.verifies()
            }
        }

        // Bob gets some cash onto the ledger from BoE
        val bc1 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            input("elbonian money 1")
            output("bob cash 1", notary = notary) { 800.DOLLARS.CASH `issued by` issuer `owned by` owner }
            command(interimOwner.owningKey) { Cash.Commands.Move() }
            this.verifies()
        }

        val bc2 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            input("elbonian money 2")
            output("bob cash 2", notary = notary) { 300.DOLLARS.CASH `issued by` issuer `owned by` owner }
            output(notary = notary) { 700.DOLLARS.CASH `issued by` issuer `owned by` interimOwner }   // Change output.
            command(interimOwner.owningKey) { Cash.Commands.Move() }
            this.verifies()
        }

        val vault = Vault<ContractState>(listOf("bob cash 1".outputStateAndRef(), "bob cash 2".outputStateAndRef()))
        return Pair(vault, listOf(eb1, bc1, bc2))
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.fillUpForSeller(
            withError: Boolean,
            issuer: PartyAndReference,
            owner: AbstractParty,
            amount: Amount<Issued<Currency>>,
            attachmentID: SecureHash?,
            notary: Party): Pair<Vault<ContractState>, List<WireTransaction>> {
        val ap = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            output("alice's paper", notary = notary) {
                CommercialPaper.State(issuer, owner, amount, TEST_TX_TIME + 7.days)
            }
            command(issuer.party.owningKey) { CommercialPaper.Commands.Issue() }
            if (!withError)
                timeWindow(time = TEST_TX_TIME)
            if (attachmentID != null)
                attachment(attachmentID)
            if (withError) {
                this.fails()
            } else {
                this.verifies()
            }
        }

        val vault = Vault<ContractState>(listOf("alice's paper".outputStateAndRef()))
        return Pair(vault, listOf(ap))
    }


    class RecordingTransactionStorage(val database: CordaPersistence, val delegate: WritableTransactionStorage) : WritableTransactionStorage {
        override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
            return database.transaction {
                delegate.track()
            }
        }

        val records: MutableList<TxRecord> = Collections.synchronizedList(ArrayList<TxRecord>())
        override val updates: Observable<SignedTransaction>
            get() = delegate.updates

        override fun addTransaction(transaction: SignedTransaction): Boolean {
            database.transaction {
                records.add(TxRecord.Add(transaction))
                delegate.addTransaction(transaction)
            }
            return true
        }

        override fun getTransaction(id: SecureHash): SignedTransaction? {
            return database.transaction {
                records.add(TxRecord.Get(id))
                delegate.getTransaction(id)
            }
        }
    }

    interface TxRecord {
        data class Add(val transaction: SignedTransaction) : TxRecord
        data class Get(val id: SecureHash) : TxRecord
    }

}
