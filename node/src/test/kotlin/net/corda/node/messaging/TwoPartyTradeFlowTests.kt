package net.corda.node.messaging

import net.corda.contracts.CommercialPaper
import net.corda.contracts.asset.*
import net.corda.contracts.testing.fillWithSomeTestCash
import net.corda.core.contracts.*
import net.corda.core.crypto.AnonymousParty
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.days
import net.corda.core.flows.FlowStateMachine
import net.corda.core.flows.StateMachineRunId
import net.corda.core.getOrThrow
import net.corda.core.map
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.*
import net.corda.core.rootCause
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.core.utilities.LogHelper
import net.corda.core.utilities.TEST_TX_TIME
import net.corda.flows.TwoPartyTradeFlow.Buyer
import net.corda.flows.TwoPartyTradeFlow.Seller
import net.corda.node.internal.AbstractNode
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.persistence.DBTransactionStorage
import net.corda.node.services.persistence.StorageServiceImpl
import net.corda.node.services.persistence.checkpoints
import net.corda.node.utilities.transaction
import net.corda.testing.*
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.Observable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.util.*
import java.util.concurrent.Future
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * In this example, Mega Corp wishes to sell their commercial paper to Mini Corp in return for $1,000,000 and they wish
 * to do it on the ledger atomically. Therefore they must work together to build a transaction.
 *
 * We assume that Mega Corp and Mini Corp already found each other via some market, and have agreed the details already.
 */
class TwoPartyTradeFlowTests {
    lateinit var net: MockNetwork

    @Before
    fun before() {
        net = MockNetwork(false)
        net.identities += MOCK_IDENTITY_SERVICE.identities
        LogHelper.setLevel("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @After
    fun after() {
        LogHelper.reset("platform.trade", "core.contract.TransactionGroup", "recordingmap")
    }

    @Test
    fun `trade cash for commercial paper`() {
        // We run this in parallel threads to help catch any race conditions that may exist. The other tests
        // we run in the unit test thread exclusively to speed things up, ensure deterministic results and
        // allow interruption half way through.
        net = MockNetwork(false, true)

        ledger {
            val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
            val megaCorpNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name)
            val miniCorpNode = net.createPartyNode(notaryNode.info.address, MINI_CORP.name)
            val megaCorpKey = megaCorpNode.services.legalIdentityKey
            val notaryKey = notaryNode.services.notaryIdentityKey

            megaCorpNode.disableDBCloseOnStop()
            miniCorpNode.disableDBCloseOnStop()

            miniCorpNode.database.transaction {
                miniCorpNode.services.fillWithSomeTestCash(2000.DOLLARS, outputNotary = notaryNode.info.notaryIdentity)
            }

            val megaCorpFakePaper = megaCorpNode.database.transaction {
                fillUpForSeller(false, megaCorpNode.info.legalIdentity.owningKey,
                        1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, null, notaryNode.info.notaryIdentity).second
            }

            insertFakeTransactions(megaCorpFakePaper, megaCorpNode, notaryNode, megaCorpKey, notaryKey)

            val (bankBStateMachine, megaCorpResult) = runBuyerAndSeller(notaryNode, megaCorpNode, miniCorpNode,
                    "Mega Corp's paper".outputStateAndRef())

            // TODO: Verify that the result was inserted into the transaction database.
            // assertEquals(bobResult.get(), megaCorpNode.storage.validatedTransactions[megaCorpResult.get().id])
            assertEquals(megaCorpResult.getOrThrow(), bankBStateMachine.getOrThrow().resultFuture.getOrThrow())

            megaCorpNode.stop()
            miniCorpNode.stop()

            megaCorpNode.database.transaction {
                assertThat(megaCorpNode.checkpointStorage.checkpoints()).isEmpty()
            }
            megaCorpNode.manuallyCloseDB()
            miniCorpNode.database.transaction {
                assertThat(miniCorpNode.checkpointStorage.checkpoints()).isEmpty()
            }
            miniCorpNode.manuallyCloseDB()
        }
    }

    @Test
    fun `shutdown and restore`() {
        ledger {
            val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
            val megaCorpNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name)
            var miniCorpNode = net.createPartyNode(notaryNode.info.address, MINI_CORP.name)
            megaCorpNode.disableDBCloseOnStop()
            miniCorpNode.disableDBCloseOnStop()
            val megaCorpKey = megaCorpNode.services.legalIdentityKey
            val notaryKey = notaryNode.services.notaryIdentityKey

            val miniCorpAddr = miniCorpNode.net.myAddress as InMemoryMessagingNetwork.PeerHandle
            val networkMapAddr = notaryNode.info.address

            net.runNetwork() // Clear network map registration messages

            miniCorpNode.database.transaction {
                miniCorpNode.services.fillWithSomeTestCash(2000.DOLLARS, outputNotary = notaryNode.info.notaryIdentity)
            }
            val megaCorpsFakePaper = megaCorpNode.database.transaction {
                fillUpForSeller(false, megaCorpNode.info.legalIdentity.owningKey,
                        1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, null, notaryNode.info.notaryIdentity).second
            }
            insertFakeTransactions(megaCorpsFakePaper, megaCorpNode, notaryNode, megaCorpKey, notaryKey)
            val megaCorpFuture = runBuyerAndSeller(notaryNode, megaCorpNode, miniCorpNode, "Mega Corp's paper".outputStateAndRef()).sellerResult

            // Everything is on this thread so we can now step through the flow one step at a time.
            // Seller Mega Corp already sent a message to Buyer Mini Corp. Pump once:
            miniCorpNode.pumpReceive()

            // Mini Corp sends a couple of queries for the dependencies back to Mega Corp. Mega Corp reponds.
            megaCorpNode.pumpReceive()
            miniCorpNode.pumpReceive()
            megaCorpNode.pumpReceive()
            miniCorpNode.pumpReceive()
            megaCorpNode.pumpReceive()
            miniCorpNode.pumpReceive()

            // OK, now Mini Corp has sent the partial transaction back to Mega Corp and is waiting for Mega Corp's signature.
            miniCorpNode.database.transaction {
                assertThat(miniCorpNode.checkpointStorage.checkpoints()).hasSize(1)
            }

            val storage = miniCorpNode.storage.validatedTransactions
            val bankBTransactionsBeforeCrash = miniCorpNode.database.transaction {
                (storage as DBTransactionStorage).transactions
            }
            assertThat(bankBTransactionsBeforeCrash).isNotEmpty

            // .. and let's imagine that Mini Corp's suffers a power cut. Mini Corp now has nothing now beyond what was on disk.
            miniCorpNode.stop()

            // Mega Corp doesn't know that and carries on: Mega Corp wants to know about the cash transactions Mini Corp's trying to use.
            // Mega Corp will wait around until Mini Corp comes back.
            assertThat(megaCorpNode.pumpReceive()).isNotNull()

            // ... bring the node back up ... the act of constructing the SMM will re-register the message handlers
            // that Mini Corp was waiting on before the reboot occurred.
            miniCorpNode = net.createNode(networkMapAddr, miniCorpAddr.id, object : MockNetwork.Factory {
                override fun create(config: NodeConfiguration, network: MockNetwork, networkMapAddr: SingleMessageRecipient?,
                                    advertisedServices: Set<ServiceInfo>, id: Int, overrideServices: Map<ServiceInfo, KeyPair>?,
                                    entropyRoot: BigInteger): MockNetwork.MockNode {
                    return MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, miniCorpAddr.id, overrideServices, entropyRoot)
                }
            }, true, MINI_CORP.name)

            // Find the future representing the result of this state machine again.
            val miniCorpFuture = miniCorpNode.smm.findStateMachines(Buyer::class.java).single().second

            // And off we go again.
            net.runNetwork()

            // Mini Corp is now finished and has the same transaction as Mega Corp.
            assertThat(miniCorpFuture.getOrThrow()).isEqualTo(megaCorpFuture.getOrThrow())

            assertThat(miniCorpNode.smm.findStateMachines(Buyer::class.java)).isEmpty()
            miniCorpNode.database.transaction {
                assertThat(miniCorpNode.checkpointStorage.checkpoints()).isEmpty()
            }
            megaCorpNode.database.transaction {
                assertThat(megaCorpNode.checkpointStorage.checkpoints()).isEmpty()
            }

            miniCorpNode.database.transaction {
                val restoredBankBTransactions = bankBTransactionsBeforeCrash.filter { miniCorpNode.storage.validatedTransactions.getTransaction(it.id) != null }
                assertThat(restoredBankBTransactions).containsAll(bankBTransactionsBeforeCrash)
            }

            megaCorpNode.manuallyCloseDB()
            miniCorpNode.manuallyCloseDB()
        }
    }

    // Creates a mock node with an overridden storage service that uses a RecordingMap, that lets us test the order
    // of gets and puts.
    private fun makeNodeWithTracking(networkMapAddr: SingleMessageRecipient?, name: String, overrideServices: Map<ServiceInfo, KeyPair>? = null): MockNetwork.MockNode {
        // Create a node in the mock network ...
        return net.createNode(networkMapAddr, -1, object : MockNetwork.Factory {
            override fun create(config: NodeConfiguration,
                                network: MockNetwork,
                                networkMapAddr: SingleMessageRecipient?,
                                advertisedServices: Set<ServiceInfo>, id: Int,
                                overrideServices: Map<ServiceInfo, KeyPair>?,
                                entropyRoot: BigInteger): MockNetwork.MockNode {
                return object : MockNetwork.MockNode(config, network, networkMapAddr, advertisedServices, id, overrideServices, entropyRoot) {
                    // That constructs the storage service object in a customised way ...
                    override fun constructStorageService(
                            attachments: AttachmentStorage,
                            transactionStorage: TransactionStorage,
                            stateMachineRecordedTransactionMappingStorage: StateMachineRecordedTransactionMappingStorage
                    ): StorageServiceImpl {
                        return StorageServiceImpl(attachments, RecordingTransactionStorage(database, transactionStorage), stateMachineRecordedTransactionMappingStorage)
                    }
                }
            }
        }, true, name, overrideServices)
    }

    @Test
    fun `check dependencies of sale asset are resolved`() {
        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val megaCorpNode = makeNodeWithTracking(notaryNode.info.address, MEGA_CORP.name)
        val miniCorpNode = makeNodeWithTracking(notaryNode.info.address, MINI_CORP.name)
        val megaCorpKey = megaCorpNode.services.legalIdentityKey

        ledger(megaCorpNode.services) {

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = megaCorpNode.database.transaction {
                attachment(ByteArrayInputStream(stream.toByteArray()))
            }

            val extraKey = miniCorpNode.keyManagement.freshKey()
            val bankBFakeCash = fillUpForBuyer(false, extraKey.public,
                    DUMMY_CASH_ISSUER.party,
                    notaryNode.info.notaryIdentity).second
            val bankBSignedTxns = insertFakeTransactions(bankBFakeCash, miniCorpNode, notaryNode, miniCorpNode.services.legalIdentityKey, extraKey)
            val megaCorpFakePaper = megaCorpNode.database.transaction {
                fillUpForSeller(false, megaCorpNode.info.legalIdentity.owningKey,
                        1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, attachmentID, notaryNode.info.notaryIdentity).second
            }
            val megaCorpSignedTxns = insertFakeTransactions(megaCorpFakePaper, megaCorpNode, notaryNode, megaCorpKey)

            net.runNetwork() // Clear network map registration messages

            runBuyerAndSeller(notaryNode, megaCorpNode, miniCorpNode, "Mega Corp's paper".outputStateAndRef())

            net.runNetwork()

            run {
                val records = (miniCorpNode.storage.validatedTransactions as RecordingTransactionStorage).records
                // Check Mini Corps's database accesses as Mini Corp's cash transactions are downloaded by Mega Corp.
                records.expectEvents(isStrict = false) {
                    sequence(
                            // Buyer Mini Corp is told about Mega Corp's commercial paper, but doesn't know it ..
                            expect(TxRecord.Get(megaCorpFakePaper[0].id)),
                            // He asks and gets the tx, validates it, sees it's a self issue with no dependencies, stores.
                            expect(TxRecord.Add(megaCorpSignedTxns.values.first())),
                            // Mega Corp gets Mini Corp's proposed transaction and doesn't know Mini Corp's two cash states. Mega Corp asks, Mini Corp answers.
                            expect(TxRecord.Get(bankBFakeCash[1].id)),
                            expect(TxRecord.Get(bankBFakeCash[2].id)),
                            // Mega Corp notices that Mini Corp's cash txns depend on a third tx she also doesn't know. Mega Corp asks, Mini Corp answers.
                            expect(TxRecord.Get(bankBFakeCash[0].id))
                    )
                }

                // Mini Corp has downloaded the attachment.
                miniCorpNode.database.transaction {
                    miniCorpNode.storage.attachments.openAttachment(attachmentID)!!.openAsJAR().use {
                        it.nextJarEntry
                        val contents = it.reader().readText()
                        assertTrue(contents.contains("Our commercial paper is top notch stuff"))
                    }
                }
            }

            // And from Mega Corp's perspective ...
            run {
                val records = (megaCorpNode.storage.validatedTransactions as RecordingTransactionStorage).records
                records.expectEvents(isStrict = false) {
                    sequence(
                            // Seller Mega Corp sends their seller info to Mini Corp, who wants to check the asset for sale.
                            // He requests, Mega Corp looks up in their DB to send the tx to Mini Corp
                            expect(TxRecord.Get(megaCorpFakePaper[0].id)),
                            // Seller Mega Corp gets a proposed tx which depends on Mini Corp's two cash txns and their own tx.
                            expect(TxRecord.Get(bankBFakeCash[1].id)),
                            expect(TxRecord.Get(bankBFakeCash[2].id)),
                            expect(TxRecord.Get(megaCorpFakePaper[0].id)),
                            // Mega Corp notices that Mini Corp's cash txns depend on a third tx she also doesn't know.
                            expect(TxRecord.Get(bankBFakeCash[0].id)),
                            // Mini Corp answers with the transactions that are now all verifiable, as Mega Corp bottomed out.
                            // Mini Corp's transactions are valid, so she commits to the database
                            expect(TxRecord.Add(bankBSignedTxns[bankBFakeCash[0].id]!!)),
                            expect(TxRecord.Get(bankBFakeCash[0].id)), // Verify
                            expect(TxRecord.Add(bankBSignedTxns[bankBFakeCash[2].id]!!)),
                            expect(TxRecord.Get(bankBFakeCash[0].id)), // Verify
                            expect(TxRecord.Add(bankBSignedTxns[bankBFakeCash[1].id]!!)),
                            // Now she verifies the transaction is contract-valid (not signature valid) which means
                            // looking up the states again.
                            expect(TxRecord.Get(bankBFakeCash[1].id)),
                            expect(TxRecord.Get(bankBFakeCash[2].id)),
                            expect(TxRecord.Get(megaCorpFakePaper[0].id)),
                            // Mega Corp needs to look up the input states to find out which Notary they point to
                            expect(TxRecord.Get(bankBFakeCash[1].id)),
                            expect(TxRecord.Get(bankBFakeCash[2].id)),
                            expect(TxRecord.Get(megaCorpFakePaper[0].id))
                    )
                }
            }
        }
    }

    @Test
    fun `track works`() {

        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val megaCorpNode = makeNodeWithTracking(notaryNode.info.address, MEGA_CORP.name)
        val miniCorpNode = makeNodeWithTracking(notaryNode.info.address, MINI_CORP.name)
        val megaCorpKey = megaCorpNode.services.legalIdentityKey

        ledger(megaCorpNode.services) {

            // Insert a prospectus type attachment into the commercial paper transaction.
            val stream = ByteArrayOutputStream()
            JarOutputStream(stream).use {
                it.putNextEntry(ZipEntry("Prospectus.txt"))
                it.write("Our commercial paper is top notch stuff".toByteArray())
                it.closeEntry()
            }
            val attachmentID = megaCorpNode.database.transaction {
                attachment(ByteArrayInputStream(stream.toByteArray()))
            }

            val bankBFakeCash = fillUpForBuyer(false, miniCorpNode.keyManagement.freshKey().public,
                    DUMMY_CASH_ISSUER.party,
                    notaryNode.info.notaryIdentity).second
            insertFakeTransactions(bankBFakeCash, miniCorpNode, notaryNode)

            val megaCorpFakePaper = megaCorpNode.database.transaction {
                fillUpForSeller(false, megaCorpNode.info.legalIdentity.owningKey,
                        1200.DOLLARS `issued by` DUMMY_CASH_ISSUER, attachmentID, notaryNode.info.notaryIdentity).second
            }

            insertFakeTransactions(megaCorpFakePaper, megaCorpNode, notaryNode, megaCorpKey)

            net.runNetwork() // Clear network map registration messages

            val megaCorpTxStream = megaCorpNode.storage.validatedTransactions.track().second
            val megaCorpTxMappings = with(megaCorpNode) { database.transaction { storage.stateMachineRecordedTransactionMapping.track().second } }
            val megaCorpSmId = runBuyerAndSeller(notaryNode, megaCorpNode, miniCorpNode,
                    "Mega Corp's paper".outputStateAndRef()).sellerId

            net.runNetwork()

            // We need to declare this here, if we do it inside [expectEvents] kotlin throws an internal compiler error(!).
            val megaCorpTxExpectations = sequence(
                    expect { tx: SignedTransaction ->
                        require(tx.id == bankBFakeCash[0].id)
                    },
                    expect { tx: SignedTransaction ->
                        require(tx.id == bankBFakeCash[2].id)
                    },
                    expect { tx: SignedTransaction ->
                        require(tx.id == bankBFakeCash[1].id)
                    }
            )
            megaCorpTxStream.expectEvents { megaCorpTxExpectations }
            val megaCorpMappingExpectations = sequence(
                    expect { mapping: StateMachineTransactionMapping ->
                        require(mapping.stateMachineRunId == megaCorpSmId)
                        require(mapping.transactionId == bankBFakeCash[0].id)
                    },
                    expect { mapping: StateMachineTransactionMapping ->
                        require(mapping.stateMachineRunId == megaCorpSmId)
                        require(mapping.transactionId == bankBFakeCash[2].id)
                    },
                    expect { mapping: StateMachineTransactionMapping ->
                        require(mapping.stateMachineRunId == megaCorpSmId)
                        require(mapping.transactionId == bankBFakeCash[1].id)
                    }
            )
            megaCorpTxMappings.expectEvents { megaCorpMappingExpectations }
        }
    }

    @Test
    fun `dependency with error on buyer side`() {
        ledger {
            runWithError(true, false, "at least one asset input")
        }
    }

    @Test
    fun `dependency with error on seller side`() {
        ledger {
            runWithError(false, true, "must be timestamped")
        }
    }

    private data class RunResult(
            // The buyer is not created immediately, only when the seller starts running
            val buyer: Future<FlowStateMachine<*>>,
            val sellerResult: Future<SignedTransaction>,
            val sellerId: StateMachineRunId
    )

    private fun runBuyerAndSeller(notaryNode: MockNetwork.MockNode,
                                  sellerNode: MockNetwork.MockNode,
                                  buyerNode: MockNetwork.MockNode,
                                  assetToSell: StateAndRef<OwnableState>): RunResult {
        val buyerFuture = buyerNode.initiateSingleShotFlow(Seller::class) { otherParty ->
            Buyer(otherParty, notaryNode.info.notaryIdentity, 1000.DOLLARS, CommercialPaper.State::class.java)
        }.map { it.stateMachine }
        val seller = Seller(buyerNode.info.legalIdentity, notaryNode.info, assetToSell, 1000.DOLLARS, sellerNode.services.legalIdentityKey)
        val sellerResultFuture = sellerNode.services.startFlow(seller).resultFuture
        return RunResult(buyerFuture, sellerResultFuture, seller.stateMachine.id)
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.runWithError(
            miniCorpError: Boolean,
            megaCorpError: Boolean,
            expectedMessageSubstring: String
    ) {
        val notaryNode = net.createNotaryNode(null, DUMMY_NOTARY.name)
        val megaCorpNode = net.createPartyNode(notaryNode.info.address, MEGA_CORP.name)
        val miniCorpNode = net.createPartyNode(notaryNode.info.address, MINI_CORP.name)
        val megaCorpKey = megaCorpNode.services.legalIdentityKey
        val miniCorpKey = miniCorpNode.services.legalIdentityKey
        val issuer = MEGA_CORP.ref(1, 2, 3)

        val miniCorpBadCash = fillUpForBuyer(miniCorpError, miniCorpKey.public, DUMMY_CASH_ISSUER.party,
                notaryNode.info.notaryIdentity).second
        val megaCorpFakePaper = megaCorpNode.database.transaction {
            fillUpForSeller(megaCorpError, megaCorpNode.info.legalIdentity.owningKey,
                    1200.DOLLARS `issued by` issuer, null, notaryNode.info.notaryIdentity).second
        }

        insertFakeTransactions(miniCorpBadCash, miniCorpNode, notaryNode, miniCorpKey)
        insertFakeTransactions(megaCorpFakePaper, megaCorpNode, notaryNode, megaCorpKey)

        net.runNetwork() // Clear network map registration messages

        val (miniCorpStateMachine, megaCorpResult) = runBuyerAndSeller(notaryNode, megaCorpNode, miniCorpNode, "Mega Corp's paper".outputStateAndRef())

        net.runNetwork()

        val e = assertFailsWith<TransactionVerificationException> {
            if (miniCorpError)
                megaCorpResult.getOrThrow()
            else
                miniCorpStateMachine.getOrThrow().resultFuture.getOrThrow()
        }
        val underlyingMessage = e.rootCause.message!!
        if (expectedMessageSubstring !in underlyingMessage) {
            assertEquals(expectedMessageSubstring, underlyingMessage)
        }
    }


    private fun insertFakeTransactions(
            wtxToSign: List<WireTransaction>,
            node: AbstractNode,
            notaryNode: MockNetwork.MockNode,
            vararg extraKeys: KeyPair): Map<SecureHash, SignedTransaction> {
        val signed: List<SignedTransaction> = signAll(wtxToSign, extraKeys.toList() + notaryNode.services.notaryIdentityKey + DUMMY_CASH_ISSUER_KEY)
        return node.database.transaction {
            node.services.recordTransactions(signed)
            val validatedTransactions = node.services.storageService.validatedTransactions
            if (validatedTransactions is RecordingTransactionStorage) {
                validatedTransactions.records.clear()
            }
            signed.associateBy { it.id }
        }
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.fillUpForBuyer(
            withError: Boolean,
            owner: PublicKey,
            issuer: AnonymousParty,
            notary: Party): Pair<Vault<ContractState>, List<WireTransaction>> {
        val interimOwnerKey = MEGA_CORP_PUBKEY
        // Mini Corp (Buyer) has some cash he got from the Bank of Elbonia, Mega Corp (Seller) has some commercial paper she
        // wants to sell to Mini Corp.
        val eb1 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            // Issued money to itself.
            output("elbonian money 1", notary = notary) { 800.DOLLARS.CASH `issued by` issuer `owned by` interimOwnerKey }
            output("elbonian money 2", notary = notary) { 1000.DOLLARS.CASH `issued by` issuer `owned by` interimOwnerKey }
            if (!withError) {
                command(issuer.owningKey) { Cash.Commands.Issue() }
            } else {
                // Put a broken command on so at least a signature is created
                command(issuer.owningKey) { Cash.Commands.Move() }
            }
            timestamp(TEST_TX_TIME)
            if (withError) {
                this.fails()
            } else {
                this.verifies()
            }
        }

        // Mini Corp gets some cash onto the ledger from BoE
        val bc1 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            input("elbonian money 1")
            output("Bank B cash 1", notary = notary) { 800.DOLLARS.CASH `issued by` issuer `owned by` owner }
            command(interimOwnerKey) { Cash.Commands.Move() }
            this.verifies()
        }

        val bc2 = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            input("elbonian money 2")
            output("Bank B cash 2", notary = notary) { 300.DOLLARS.CASH `issued by` issuer `owned by` owner }
            output(notary = notary) { 700.DOLLARS.CASH `issued by` issuer `owned by` interimOwnerKey }   // Change output.
            command(interimOwnerKey) { Cash.Commands.Move() }
            this.verifies()
        }

        val vault = Vault<ContractState>(listOf("Bank B cash 1".outputStateAndRef(), "Bank B cash 2".outputStateAndRef()))
        return Pair(vault, listOf(eb1, bc1, bc2))
    }

    private fun LedgerDSL<TestTransactionDSLInterpreter, TestLedgerDSLInterpreter>.fillUpForSeller(
            withError: Boolean,
            owner: PublicKey,
            amount: Amount<Issued<Currency>>,
            attachmentID: SecureHash?,
            notary: Party): Pair<Vault<ContractState>, List<WireTransaction>> {
        val ap = transaction(transactionBuilder = TransactionBuilder(notary = notary)) {
            output("Mega Corp's paper", notary = notary) {
                CommercialPaper.State(MEGA_CORP.ref(1, 2, 3), owner, amount, TEST_TX_TIME + 7.days)
            }
            command(MEGA_CORP_PUBKEY) { CommercialPaper.Commands.Issue() }
            if (!withError)
                timestamp(time = TEST_TX_TIME)
            if (attachmentID != null)
                attachment(attachmentID)
            if (withError) {
                this.fails()
            } else {
                this.verifies()
            }
        }

        val vault = Vault<ContractState>(listOf("Mega Corp's paper".outputStateAndRef()))
        return Pair(vault, listOf(ap))
    }


    class RecordingTransactionStorage(val database: Database, val delegate: TransactionStorage) : TransactionStorage {
        override fun track(): Pair<List<SignedTransaction>, Observable<SignedTransaction>> {
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
