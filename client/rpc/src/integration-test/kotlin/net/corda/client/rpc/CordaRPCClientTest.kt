package net.corda.client.rpc

import net.corda.client.rpc.internal.createCordaRPCClientWithSslAndClassLoader
import net.corda.core.context.*
import net.corda.core.contracts.FungibleAsset
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.flatMap
import net.corda.core.internal.location
import net.corda.core.internal.toPath
import net.corda.core.messaging.*
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.USD
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.internal.NodeWithInfo
import net.corda.node.services.Permissions.Companion.all
import net.corda.testing.common.internal.checkNotOnClasspath
import net.corda.testing.core.*
import net.corda.testing.node.User
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.internal.NodeBasedTest
import net.corda.testing.node.internal.ProcessUtilities
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import rx.subjects.PublishSubject
import java.net.URLClassLoader
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CordaRPCClientTest : NodeBasedTest(listOf("net.corda.finance")) {
    companion object {
        val rpcUser = User("user1", "test", permissions = setOf(all()))

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName())
    }

    private lateinit var node: NodeWithInfo
    private lateinit var identity: Party
    private lateinit var client: CordaRPCClient
    private var connection: CordaRPCConnection? = null

    private fun login(username: String, password: String, externalTrace: Trace? = null, impersonatedActor: Actor? = null) {
        connection = client.start(username, password, externalTrace, impersonatedActor)
    }

    @Before
    override fun setUp() {
        super.setUp()
        node = startNode(ALICE_NAME, rpcUsers = listOf(rpcUser))
        client = CordaRPCClient(node.node.configuration.rpcOptions.address, CordaRPCClientConfiguration.DEFAULT.copy(
            maxReconnectAttempts = 5
        ))
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
                        successful = (node.node.started == null)
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

    // WireTransaction stores its components as blobs which are deserialised in its constructor. This test makes sure
    // the extra class loader given to the CordaRPCClient is used in this deserialisation, as otherwise any WireTransaction
    // containing Cash.State objects are not receivable by the client.
    //
    // We run the client in a separate process, without the finance module on its system classpath to ensure that the
    // additional class loader that we give it is used. Cash.State objects are used as they can't be synthesised fully
    // by the carpenter, and thus avoiding any false-positive results.
    @Test
    fun `additional class loader used by WireTransaction when it deserialises its components`() {
        val financeLocation = Cash::class.java.location.toPath().toString()
        val classPathWithoutFinance = ProcessUtilities.defaultClassPath.filter { financeLocation !in it }

        // Create a Cash.State object for the StandaloneCashRpcClient to get
        node.services.startFlow(CashIssueFlow(100.POUNDS, OpaqueBytes.of(1), identity), InvocationContext.shell()).flatMap { it.resultFuture }.getOrThrow()
        val outOfProcessRpc = ProcessUtilities.startJavaProcess<StandaloneCashRpcClient>(
                classPath = classPathWithoutFinance,
                arguments = listOf(node.node.configuration.rpcOptions.address.toString(), financeLocation)
        )
        assertThat(outOfProcessRpc.waitFor()).isZero()  // i.e. no exceptions were thrown
    }

    private fun checkShellNotification(info: StateMachineInfo) {
        val context = info.invocationContext
        assertThat(context.origin).isInstanceOf(InvocationOrigin.Shell::class.java)
    }

    private fun checkRpcNotification(info: StateMachineInfo,
                                     rpcUsername: String,
                                     historicalIds: MutableSet<Trace.InvocationId>,
                                     externalTrace: Trace?,
                                     impersonatedActor: Actor?) {
        val context = info.invocationContext
        assertThat(context.origin).isInstanceOf(InvocationOrigin.RPC::class.java)
        assertThat(context.externalTrace).isEqualTo(externalTrace)
        assertThat(context.impersonatedActor).isEqualTo(impersonatedActor)
        assertThat(context.actor?.id?.value).isEqualTo(rpcUsername)
        assertThat(historicalIds).doesNotContain(context.trace.invocationId)
        historicalIds.add(context.trace.invocationId)
    }

    private object StandaloneCashRpcClient {
        @JvmStatic
        fun main(args: Array<String>) {
            checkNotOnClasspath("net.corda.finance.contracts.asset.Cash") {
                "The finance module cannot be on the system classpath"
            }
            val address = NetworkHostAndPort.parse(args[0])
            val financeClassLoader = URLClassLoader(arrayOf(Paths.get(args[1]).toUri().toURL()))
            val rpcUser = CordaRPCClientTest.rpcUser
            val client = createCordaRPCClientWithSslAndClassLoader(address, classLoader = financeClassLoader)
            val state = client.use(rpcUser.username, rpcUser.password) {
                // financeClassLoader should be allowing the Cash.State to materialise
                @Suppress("DEPRECATION")
                it.proxy.internalVerifiedTransactionsSnapshot()[0].tx.outputsOfType<FungibleAsset<*>>()[0]
            }
            assertThat(state.javaClass.name).isEqualTo("net.corda.finance.contracts.asset.Cash${'$'}State")
            assertThat(state.amount.quantity).isEqualTo(10000)
            assertThat(state.amount.token.product).isEqualTo(Currency.getInstance("GBP"))
            // This particular check assures us that the Cash.State we have hasn't been carpented.
            assertThat(state.participants).isEqualTo(listOf(state.owner))
        }
    }
}
