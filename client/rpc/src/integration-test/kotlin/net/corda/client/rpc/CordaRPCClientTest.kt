package net.corda.client.rpc

import net.corda.core.context.*
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.packageName
import net.corda.core.messaging.*
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.USD
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.finance.schemas.CashSchemaV1
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.core.*
import net.corda.testing.node.User
import net.corda.testing.node.internal.NodeBasedTest
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaRPCClientTest : NodeBasedTest(listOf("net.corda.finance.contracts", CashSchemaV1::class.packageName)) {
    private val rpcUser = User("user1", "test", permissions = setOf(all())
    )
    private lateinit var node: StartedNode<Node>
    private lateinit var identity: Party
    private lateinit var client: CordaRPCClient
    private var connection: CordaRPCConnection? = null

    private fun login(username: String, password: String, externalTrace: Trace? = null, impersonatedActor: Actor? = null) {
        connection = client.start(username, password, externalTrace, impersonatedActor)
    }

    @Before
    fun setUp() {
        node = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        client = CordaRPCClient(node.internals.configuration.rpcOptions.address!!, object : CordaRPCClientConfiguration {
            override val maxReconnectAttempts = 5
        })
        identity = node.info.identityFromX500Name(ALICE_NAME)
    }

    @After
    fun done() {
        connection?.close()
    }

    @Test
    fun `log in with valid username and password`() {
        login(rpcUser.username, rpcUser.password)
    }

    @Test
    fun `log in with unknown user`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            login(random63BitValue().toString(), rpcUser.password)
        }
    }

    @Test
    fun `log in with incorrect password`() {
        assertThatExceptionOfType(ActiveMQSecurityException::class.java).isThrownBy {
            login(rpcUser.username, random63BitValue().toString())
        }
    }

    @Test
    fun `shutdown command stops the node`() {

        val nodeIsShut: PublishSubject<Unit> = PublishSubject.create()
        val latch = CountDownLatch(1)
        var successful = false
        val maxCount = 20
        var count = 0
        CloseableExecutor(Executors.newSingleThreadScheduledExecutor()).use { scheduler ->

            val task = scheduler.scheduleAtFixedRate({
                try {
                    println("Checking whether node is still running...")
                    client.start(rpcUser.username, rpcUser.password).use {
                        println("... node is still running.")
                        if (count == maxCount) {
                            nodeIsShut.onError(AssertionError("Node does not get shutdown by RPC"))
                        }
                        count++
                    }
                } catch (e: RPCException) {
                    println("... node is not running.")
                    nodeIsShut.onCompleted()
                } catch (e: ActiveMQSecurityException) {
                    // nothing here - this happens if trying to connect before the node is started
                } catch (e: Throwable) {
                    nodeIsShut.onError(e)
                }
            }, 1, 1, TimeUnit.SECONDS)

            nodeIsShut.doOnError { error ->
                error.printStackTrace()
                successful = false
                task.cancel(true)
                latch.countDown()
            }.doOnCompleted {
                        successful = (node.internals.started == null)
                        task.cancel(true)
                        latch.countDown()
                    }.subscribe()

            client.start(rpcUser.username, rpcUser.password).use { rpc -> rpc.proxy.shutdown() }

            latch.await()
            assertThat(successful).isTrue()
        }
    }

    private class CloseableExecutor(private val delegate: ScheduledExecutorService) : AutoCloseable, ScheduledExecutorService by delegate {

        override fun close() {
            delegate.shutdown()
        }
    }

    @Test
    fun `close-send deadlock and premature shutdown on empty observable`() {
        println("Starting client")
        login(rpcUser.username, rpcUser.password)
        println("Creating proxy")
        println("Starting flow")
        val flowHandle = connection!!.proxy.startTrackedFlow(::CashIssueFlow,
                20.DOLLARS, OpaqueBytes.of(0), identity
        )
        println("Started flow, waiting on result")
        flowHandle.progress.subscribe {
            println("PROGRESS $it")
        }
        println("Result: ${flowHandle.returnValue.getOrThrow()}")
    }

    @Test
    fun `check basic flow has no progress`() {
        login(rpcUser.username, rpcUser.password)
        connection!!.proxy.startFlow(::CashPaymentFlow, 100.DOLLARS, identity).use {
            assertFalse(it is FlowProgressHandle<*>)
        }
    }

    @Test
    fun `get cash balances`() {
        login(rpcUser.username, rpcUser.password)
        val proxy = connection!!.proxy
        val startCash = proxy.getCashBalances()
        assertTrue(startCash.isEmpty(), "Should not start with any cash")

        val flowHandle = proxy.startFlow(::CashIssueFlow,
                123.DOLLARS, OpaqueBytes.of(0), identity
        )
        println("Started issuing cash, waiting on result")
        flowHandle.returnValue.get()

        val cashDollars = proxy.getCashBalance(USD)
        println("Balance: $cashDollars")
        assertEquals(123.DOLLARS, cashDollars)
    }

    @Test
    fun `flow initiator via RPC`() {
        val externalTrace = Trace.newInstance()
        val impersonatedActor = Actor(Actor.Id("Mark Dadada"), AuthServiceId("Test"), owningLegalIdentity = BOB_NAME)
        login(rpcUser.username, rpcUser.password, externalTrace, impersonatedActor)
        val proxy = connection!!.proxy

        val updates = proxy.stateMachinesFeed().updates

        node.services.startFlow(CashIssueFlow(2000.DOLLARS, OpaqueBytes.of(0), identity), InvocationContext.shell()).flatMap { it.resultFuture }.getOrThrow()
        proxy.startFlow(::CashIssueFlow, 123.DOLLARS, OpaqueBytes.of(0), identity).returnValue.getOrThrow()
        proxy.startFlowDynamic(CashIssueFlow::class.java, 1000.DOLLARS, OpaqueBytes.of(0), identity).returnValue.getOrThrow()

        val historicalIds = mutableSetOf<Trace.InvocationId>()
        var sessionId: Trace.SessionId? = null
        updates.expectEvents(isStrict = false) {
            sequence(
                    expect { update: StateMachineUpdate.Added ->
                        checkShellNotification(update.stateMachineInfo)
                    },
                    expect { update: StateMachineUpdate.Added ->
                        checkRpcNotification(update.stateMachineInfo, rpcUser.username, historicalIds, externalTrace, impersonatedActor)
                        sessionId = update.stateMachineInfo.invocationContext.trace.sessionId
                    },
                    expect { update: StateMachineUpdate.Added ->
                        checkRpcNotification(update.stateMachineInfo, rpcUser.username, historicalIds, externalTrace, impersonatedActor)
                        assertThat(update.stateMachineInfo.invocationContext.trace.sessionId).isEqualTo(sessionId)
                    }
            )
        }
    }
}

private fun checkShellNotification(info: StateMachineInfo) {
    val context = info.invocationContext
    assertThat(context.origin).isInstanceOf(InvocationOrigin.Shell::class.java)
}

private fun checkRpcNotification(info: StateMachineInfo, rpcUsername: String, historicalIds: MutableSet<Trace.InvocationId>, externalTrace: Trace?, impersonatedActor: Actor?) {
    val context = info.invocationContext
    assertThat(context.origin).isInstanceOf(InvocationOrigin.RPC::class.java)
    assertThat(context.externalTrace).isEqualTo(externalTrace)
    assertThat(context.impersonatedActor).isEqualTo(impersonatedActor)
    assertThat(context.actor?.id?.value).isEqualTo(rpcUsername)
    assertThat(historicalIds).doesNotContain(context.trace.invocationId)
    historicalIds.add(context.trace.invocationId)
}
