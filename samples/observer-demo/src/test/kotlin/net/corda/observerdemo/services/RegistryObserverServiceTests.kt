package net.corda.observerdemo.services

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.NonEmptySet
import net.corda.core.utilities.seconds
import net.corda.finance.USD
import net.corda.node.internal.StartedNode
import net.corda.observerdemo.Observed
import net.corda.observerdemo.contracts.Commands
import net.corda.observerdemo.contracts.Receivable
import net.corda.observerdemo.contracts.ReceivableContract
import net.corda.observerdemo.flow.RegistryObserverFlow
import net.corda.testing.DUMMY_NOTARY
import net.corda.testing.MINI_CORP
import net.corda.testing.TEST_TX_TIME
import net.corda.testing.getDefaultNotary
import net.corda.testing.node.MockAttachmentStorage
import net.corda.testing.node.MockCordappProvider
import net.corda.testing.node.MockNetwork
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Integration test of the registry service observing transactions being written to the ledger.
 */
class RegistryObserverServiceTests {
    lateinit var net: MockNetwork
    lateinit var notaryNode: StartedNode<MockNetwork.MockNode>
    lateinit var clientNode: StartedNode<MockNetwork.MockNode>

    @Before fun setup() {
        net = MockNetwork(cordappPackages = listOf("net.corda.observerdemo"))
        notaryNode = net.createNotaryNode(legalName = DUMMY_NOTARY.name)
        clientNode = net.createNode(legalName = MINI_CORP.name)
        net.runNetwork() // Clear network map registration messages
    }

    @Test fun `should sign a unique transaction with a valid timestamp`() {
        val stx = run {
            val notary = notaryNode.services.getDefaultNotary()
            val observer = notaryNode.info.identityFromX500Name(DUMMY_NOTARY.name)
            val client = clientNode.info.identityFromX500Name(MINI_CORP.name)
            val inputState =
                    Receivable(
                            UniqueIdentifier.fromString("9e688c58-a548-3b8e-af69-c9e1005ad0bf"),
                            observer,
                            (TEST_TX_TIME - Duration.ofDays(2)).atZone(ZoneId.of("UTC")),
                            TEST_TX_TIME - Duration.ofDays(1),
                            client,
                            Amount(1000L, USD),
                            client
                    )
            val issueCommand = Command(Commands.Issue(changed = NonEmptySet.of(inputState.linearId)),
                    client.owningKey)
            val observedCommand = Command(Observed(), observer.owningKey)
            val ptx = TransactionBuilder(notary).apply {
                addOutputState(TransactionState(inputState, ReceivableContract.PROGRAM_ID, notary))
                addCommand(issueCommand)
                addCommand(observedCommand)
            }
            ptx.setTimeWindow(Instant.now(), 30.seconds)
            clientNode.services.signInitialTransaction(ptx)
        }

        net.runNetwork()

        val flow = RegistryObserverFlow.Client(stx)
        val future = clientNode.services.startFlow(flow).resultFuture
        net.runNetwork()

        // Ensure the flow actually completes
        future.get()
    }
}
