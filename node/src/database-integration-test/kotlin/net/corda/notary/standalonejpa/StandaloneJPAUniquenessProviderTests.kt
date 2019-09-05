package net.corda.notary.standalonejpa

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.AlwaysAcceptAttachmentConstraint
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TimeWindow
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.crypto.sha256
import net.corda.core.flows.NotaryError
import net.corda.core.flows.NotaryException
import net.corda.core.flows.NotaryFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.node.NotaryInfo
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.Try
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.services.config.NotaryConfig
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.SchemaMigration
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.contracts.DummyContract
import net.corda.testing.core.dummyCommand
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.TestClock
import net.corda.testing.node.internal.*
import org.hamcrest.Matchers
import org.junit.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigInteger
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ExecutionException
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(Parameterized::class)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class StandaloneJPAUniquenessProviderTests(val dataSourceFactory: DataSourceFactory) {
    companion object {

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<DataSourceFactory> = listOf(
                MySQLDataSourceFactory("mysql:5.7.24"),
                MySQLDataSourceFactory("mysql:8.0"),
                MySQLDataSourceFactory("percona:5.7"),
                MariaDBDataSourceFactory("mariadb:10.4.6"),
                PostgreSQLDataSourceFactory("postgres:9.6"),
                CockroachDBDataSourceFactory("cockroachdb/cockroach:v19.1.2"),
                SQLServerDataSourceFactory("mcr.microsoft.com/mssql/server:2017-CU13"),
                OracleDataSourceFactory("oracleinanutshell/oracle-xe-11g"),
                OracleDataSourceFactory("medgetablelevvel/oracle-12c-base")
        )

        var mockNet: InternalMockNetwork = InternalMockNetwork(
                cordappsForAllNodes = listOf(DUMMY_CONTRACTS_CORDAPP, enclosedCordapp()),

                initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                notarySpecs = emptyList())

        private lateinit var node: TestStartedNode
        val notaryName = CordaX500Name("MySQL Notary Service", "Zurich", "CH")
        val notaryNodeName = CordaX500Name("Notary Replica 1", "Zurich", "CH")
        private lateinit var notaryParty: Party
        private lateinit var notaryNode: TestStartedNode
        private lateinit var dataSourceProperties: Properties
        private var dataSourceFactoryList: MutableList<DataSourceFactory> = arrayListOf()

        /** The data source factory that we are using for this particular batch of test runs. This will change every time we move to a new database while testing.*/
        private var currentDataSourceFactory: DataSourceFactory? = null

        @AfterClass
        @JvmStatic
        fun stopNetwork() {
            dataSourceFactoryList.forEach {
                it.close()
            }
            mockNet.stopNodes()
        }
    }

    @Before
    fun before() {
        dataSourceFactoryList.add(dataSourceFactory)

        if (currentDataSourceFactory?.dockerImageName != dataSourceFactory.dockerImageName) {
            currentDataSourceFactory?.close()
            currentDataSourceFactory = dataSourceFactory
            currentDataSourceFactory!!.startup()
            dataSourceProperties = currentDataSourceFactory!!.getDataSourceProperties()

            /** CockroachDB is incompatible with Liquibase because it does not support the addition of a primary key after table creation. */
            if (!currentDataSourceFactory!!.dockerImageName.contains("cockroach")) {
                val dataSource = HibernateEntityManagerFactoryProvider.createDataSource(dataSourceProperties, null)
                val loader = JarScanningCordappLoader.fromDirectories(setOf(Paths.get(System.getProperty("user.dir"))))
                val schemaMigration = SchemaMigration(setOf(JPANotarySchemaV1), dataSource, DatabaseConfig(runMigration = true), loader, Paths.get(System.getProperty("user.dir")), ourName = notaryName)
                schemaMigration.runMigration(false, null)
            }

            notaryParty = DevIdentityGenerator.generateDistributedNotarySingularIdentity(listOf(mockNet.baseDirectory(mockNet.nextNodeId)), notaryName)
            val networkParameters = NetworkParametersCopier(testNetworkParameters(notaries = listOf(NotaryInfo(notaryParty, false)), minimumPlatformVersion = 4))
            val notaryNodeUnstarted = createNotaryNode()
            val nodeUnstarted = mockNet.createUnstartedNode()
            val startedNodes = listOf(notaryNodeUnstarted, nodeUnstarted).map { n ->
                networkParameters.install(mockNet.baseDirectory(n.id))
                n.start()
            }
            mockNet.startNodes()
            notaryNode = startedNodes.first()
            node = startedNodes.last()
        }
    }

    private fun createNotaryNode(): InternalMockNetwork.MockNode {
        val jpaDatabaseConfig = StandaloneJPANotaryDatabaseConfig(initialiseSchema = SchemaInitializationType.VALIDATE)
        val config = StandaloneJPANotaryConfig(dataSourceProperties, jpaDatabaseConfig)

        return mockNet.createUnstartedNode(
                InternalMockNodeParameters(legalName = notaryNodeName,
                        entropyRoot = BigInteger.valueOf(60L),
                        configOverrides = {
                            val notary = NotaryConfig(
                                    validating = false,
                                    jpa = config,
                                    serviceLegalName = notaryName
                            )
                            doReturn(notary).whenever(it).notary
                        }
                )
        )
    }

    private fun TestStartedNode.signInitialTransaction(notary: Party, block: TransactionBuilder.() -> Any?): SignedTransaction {
        return services.signInitialTransaction(
                TransactionBuilder(notary).apply {
                    addCommand(dummyCommand(services.myInfo.singleIdentity().owningKey))
                    block()
                }
        )
    }

    private fun verifySignatures(signatures: List<TransactionSignature>, txId: SecureHash) {
        notaryParty.owningKey.isFulfilledBy(signatures.map { it.by })
        signatures.forEach { it.verify(txId) }
    }

    @Test
    fun `sign transaction test`() {
        node.run {
            val issueTx = signInitialTransaction(notaryParty) {
                setTimeWindow(services.clock.instant(), 30.seconds)
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            println("Current time ${services.clock.instant()}")
            val resultFuture = services.startFlow(NotaryFlow.Client(issueTx)).resultFuture

            mockNet.runNetwork()
            val signatures = resultFuture.get()
            verifySignatures(signatures, issueTx.id)
        }
    }

    @Test
    fun `detect double spend`() {
        node.run {
            val issueTx = signInitialTransaction(notaryParty) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            services.recordTransactions(issueTx)
            val spendTxs = (1..10).map {
                signInitialTransaction(notaryParty) {
                    addInputState(issueTx.tx.outRef<ContractState>(0))
                }
            }
            assertEquals(spendTxs.size, spendTxs.map { it.id }.distinct().size)
            val flows = spendTxs.map { NotaryFlow.Client(it) }
            val stateMachines = flows.map { services.startFlow(it) }
            mockNet.runNetwork()
            val results = stateMachines.map { Try.on { it.resultFuture.getOrThrow() } }
            println("Result count: ${results.count()}")
            results.forEach {
                println("IsSuccess: ${it.isSuccess}")
            }
            val successfulIndex = results.asSequence().mapIndexedNotNull { index, result ->
                if (result is Try.Success) {
                    index
                } else {
                    null
                }
            }.single()
            spendTxs.zip(results).forEach { (tx, result) ->
                if (result is Try.Failure) {
                    val exception = result.exception as NotaryException
                    val error = exception.error as NotaryError.Conflict
                    assertEquals(tx.id, error.txId)
                    val (stateRef, cause) = error.consumedStates.entries.single()
                    assertEquals(StateRef(issueTx.id, 0), stateRef)
                    assertEquals(spendTxs[successfulIndex].id.sha256(), cause.hashOfTransactionId)
                }
            }
        }
    }

    @Test
    fun `transactions outside their time window are rejected`() {
        node.run {
            val issueTx = signInitialTransaction(notaryParty) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            services.recordTransactions(issueTx)
            val spendTx = signInitialTransaction(notaryParty) {
                addInputState(issueTx.tx.outRef<ContractState>(0))
                setTimeWindow(TimeWindow.fromOnly(Instant.MAX))
            }
            val flow = NotaryFlow.Client(spendTx)
            val resultFuture = services.startFlow(flow).resultFuture
            mockNet.runNetwork()
            val exception = assertFailsWith<ExecutionException> { resultFuture.get() }
            Assert.assertThat(exception.cause, Matchers.instanceOf(NotaryException::class.java))
            val error = (exception.cause as NotaryException).error
            Assert.assertThat(error, Matchers.instanceOf(NotaryError.TimeWindowInvalid::class.java))
        }
    }

    @Test
    fun `notarise issue tx with time-window`() {
        node.run {
            val issueTx = signInitialTransaction(notaryParty) {
                setTimeWindow(services.clock.instant(), 30.seconds)
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            println("Current time ${services.clock.instant()}")
            val resultFuture = services.startFlow(NotaryFlow.Client(issueTx)).resultFuture

            mockNet.runNetwork()
            val signatures = resultFuture.get()
            verifySignatures(signatures, issueTx.id)
        }
    }

    @Test
    fun `transactions can be re-notarised outside their time window`() {
        node.run {
            val issueTx = signInitialTransaction(notaryParty) {
                addOutputState(DummyContract.SingleOwnerState(owner = info.singleIdentity()), DummyContract.PROGRAM_ID, AlwaysAcceptAttachmentConstraint)
            }
            services.recordTransactions(issueTx)
            val spendTx = signInitialTransaction(notaryParty) {
                addInputState(issueTx.tx.outRef<ContractState>(0))
                setTimeWindow(TimeWindow.untilOnly(Instant.now() + Duration.ofHours(1)))
            }
            val resultFuture = services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
            mockNet.runNetwork()
            val signatures = resultFuture.get()
            verifySignatures(signatures, spendTx.id)

            advanceAllClocks(Duration.ofDays(1))

            val resultFuture2 = services.startFlow(NotaryFlow.Client(spendTx)).resultFuture
            mockNet.runNetwork()
            val signatures2 = resultFuture2.get()
            verifySignatures(signatures2, spendTx.id)

            resetAllClocks()
        }
    }

    private fun advanceAllClocks(by: Duration) {
        for (node in mockNet.nodes) {
            (node.started!!.services.clock as TestClock).advanceBy(by)
        }
    }

    private fun resetAllClocks() {
        for (node in mockNet.nodes) {
            (node.started!!.services.clock as TestClock).setTo(Instant.now())
        }
    }
}