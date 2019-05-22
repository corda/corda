package net.corda.testing.node.internal

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.client.rpc.ConnectionFailureException
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.div
import net.corda.core.internal.times
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._allEnabledSerializationEnvs
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.configOf
import net.corda.node.services.config.parseToDbSchemaFriendlyName
import net.corda.node.services.messaging.Message
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.TransactionIsolationLevel
import net.corda.testing.database.DatabaseConstants
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeHandle
import net.corda.testing.internal.*
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.testContext
import org.apache.commons.lang3.ClassUtils
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.springframework.jdbc.datasource.DataSourceUtils
import org.springframework.jdbc.datasource.DriverManagerDataSource
import rx.Observable
import rx.subjects.AsyncSubject
import java.io.InputStream
import java.net.Socket
import java.net.SocketException
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.reflect.KClass

private val log = LoggerFactory.getLogger("net.corda.testing.internal.InternalTestUtils")

/**
 * Reference to the finance-contracts CorDapp in this repo. The metadata is taken directly from finance/contracts/build.gradle, including the
 * fact that the jar is signed. If you need an unsigned jar then use `cordappWithPackages("net.corda.finance.contracts")`.
 *
 * You will probably need to use [FINANCE_CORDAPPS] instead to get access to the flows as well.
 */
@JvmField
val FINANCE_CONTRACTS_CORDAPP: TestCordappImpl = findCordapp("net.corda.finance.contracts")

/**
 * Reference to the finance-workflows CorDapp in this repo. The metadata is taken directly from finance/workflows/build.gradle, including the
 * fact that the jar is signed. If you need an unsigned jar then use `cordappWithPackages("net.corda.finance.flows")`.
 *
 * You will probably need to use [FINANCE_CORDAPPS] instead to get access to the contract classes as well.
 */
@JvmField
val FINANCE_WORKFLOWS_CORDAPP: TestCordappImpl = findCordapp("net.corda.finance.workflows")

@JvmField
val FINANCE_CORDAPPS: Set<TestCordappImpl> = setOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP)

/**
 * *Custom* CorDapp containing the contents of the `net.corda.testing.contracts` package, i.e. the dummy contracts. This is not a real CorDapp
 * in the way that [FINANCE_CONTRACTS_CORDAPP] and [FINANCE_WORKFLOWS_CORDAPP] are.
 */
@JvmField
val DUMMY_CONTRACTS_CORDAPP: CustomCordapp = cordappWithPackages("net.corda.testing.contracts")

@JvmField
val BUSINESS_NETWORK_CORDAPP: TestCordappImpl = findCordapp("net.corda.sample.businessnetwork")

fun cordappsForPackages(vararg packageNames: String): Set<CustomCordapp> = cordappsForPackages(packageNames.asList())

fun cordappsForPackages(packageNames: Iterable<String>): Set<CustomCordapp> {
    return simplifyScanPackages(packageNames).mapTo(HashSet()) { cordappWithPackages(it) }
}

/**
 * Create a *custom* CorDapp which contains all the classes and resoures located in the given packages. The CorDapp's metadata will be the
 * default values as defined in the [CustomCordapp] c'tor. Use the `copy` to change them. This means the metadata will *not* be the one defined
 * in the original CorDapp(s) that the given packages may represent. If this is not what you want then use [findCordapp] instead.
 */
fun cordappWithPackages(vararg packageNames: String): CustomCordapp = CustomCordapp(packages = simplifyScanPackages(packageNames.asList()))

/** Create a *custom* CorDapp which contains just the given classes. */
// TODO Rename to cordappWithClasses
fun cordappForClasses(vararg classes: Class<*>): CustomCordapp = CustomCordapp(packages = emptySet(), classes = classes.toSet())

/**
 * Find the single CorDapp jar on the current classpath which contains the given package. This is a convenience method for
 * [TestCordapp.findCordapp] but returns the internal [TestCordappImpl].
 */
fun findCordapp(scanPackage: String): TestCordappImpl = TestCordapp.findCordapp(scanPackage) as TestCordappImpl

/** Create a *custom* CorDapp which just contains the enclosed classes of the receiver class. */
fun Any.enclosedCordapp(): CustomCordapp {
    val receiverClass = javaClass.enclosingClass ?: javaClass // In case this is called in the companion object
    val classes = HashSet<Class<*>>()
    receiverClass.collectEnclosedClasses(classes)
    ClassUtils.getAllSuperclasses(receiverClass).forEach { (it as Class<*>).collectEnclosedClasses(classes) }
    ClassUtils.getAllInterfaces(receiverClass).forEach { (it as Class<*>).collectEnclosedClasses(classes) }
    require(classes.isNotEmpty()) { "${receiverClass.name} does not contain any enclosed classes to build a CorDapp out of" }
    return CustomCordapp(name = receiverClass.name, classes = classes)
}

private fun Class<*>.collectEnclosedClasses(classes: MutableSet<Class<*>>) {
    val enclosedClasses = declaredClasses
    if (enclosedClasses.isNotEmpty() || enclosingClass != null) {
        classes += this
    }
    enclosedClasses.forEach { it.collectEnclosedClasses(classes) }
}

private fun getCallerClass(directCallerClass: KClass<*>): Class<*>? {
    val stackTrace = Throwable().stackTrace
    val index = stackTrace.indexOfLast { it.className == directCallerClass.java.name }
    if (index == -1) return null
    return try {
        Class.forName(stackTrace[index + 1].className)
    } catch (e: ClassNotFoundException) {
        null
    }
}

fun getCallerPackage(directCallerClass: KClass<*>): String? = getCallerClass(directCallerClass)?.`package`?.name

/**
 * Squashes child packages if the parent is present. Example: ["com.foo", "com.foo.bar"] into just ["com.foo"].
 */
@VisibleForTesting
internal fun simplifyScanPackages(scanPackages: Iterable<String>): Set<String> {
    return scanPackages.sorted().fold(emptySet()) { soFar, packageName ->
        when {
            soFar.isEmpty() -> setOf(packageName)
            packageName.startsWith("${soFar.last()}.") -> soFar
            else -> soFar + packageName
        }
    }
}

/**
 * @throws ListenProcessDeathException if [listenProcess] dies before the check succeeds, i.e. the check can't succeed as intended.
 */
fun addressMustBeBound(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, listenProcess: Process? = null) {
    addressMustBeBoundFuture(executorService, hostAndPort, listenProcess).getOrThrow()
}

fun addressMustBeBoundFuture(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, listenProcess: Process? = null): CordaFuture<Unit> {
    return poll(executorService, "address $hostAndPort to bind") {
        if (listenProcess != null && !listenProcess.isAlive) {
            throw ListenProcessDeathException(hostAndPort, listenProcess)
        }
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            Unit
        } catch (_exception: SocketException) {
            null
        }
    }
}

/*
 * The default timeout value of 40 seconds have been chosen based on previous node shutdown time estimate.
 * It's been observed that nodes can take up to 30 seconds to shut down, so just to stay on the safe side the 60 seconds
 * timeout has been chosen.
 */
fun addressMustNotBeBound(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort, timeout: Duration = 40.seconds) {
    addressMustNotBeBoundFuture(executorService, hostAndPort).getOrThrow(timeout)
}

fun addressMustNotBeBoundFuture(executorService: ScheduledExecutorService, hostAndPort: NetworkHostAndPort): CordaFuture<Unit> {
    return poll(executorService, "address $hostAndPort to unbind") {
        try {
            Socket(hostAndPort.host, hostAndPort.port).close()
            null
        } catch (_exception: SocketException) {
            Unit
        }
    }
}

fun <A> poll(
        executorService: ScheduledExecutorService,
        pollName: String,
        pollInterval: Duration = 500.millis,
        warnCount: Int = 120,
        check: () -> A?
): CordaFuture<A> {
    val resultFuture = openFuture<A>()
    val task = object : Runnable {
        var counter = -1
        override fun run() {
            if (resultFuture.isCancelled) return // Give up, caller can no longer get the result.
            if (++counter == warnCount) {
                log.warn("Been polling $pollName for ${(pollInterval * warnCount.toLong()).seconds} seconds...")
            }
            try {
                val checkResult = check()
                if (checkResult != null) {
                    resultFuture.set(checkResult)
                } else {
                    executorService.schedule(this, pollInterval.toMillis(), TimeUnit.MILLISECONDS)
                }
            } catch (e: Exception) {
                resultFuture.setException(e)
            }
        }
    }
    executorService.submit(task) // The check may be expensive, so always run it in the background even the first time.
    return resultFuture
}

class ListenProcessDeathException(hostAndPort: NetworkHostAndPort, listenProcess: Process) :
        CordaException("The process that was expected to listen on $hostAndPort has died with status: ${listenProcess.exitValue()}")

fun <T> StartedNodeServices.startFlow(logic: FlowLogic<T>): FlowStateMachine<T> = startFlow(logic, newContext()).getOrThrow()

fun StartedNodeServices.newContext(): InvocationContext = testContext(myInfo.chooseIdentity().name)

fun InMemoryMessagingNetwork.MessageTransfer.getMessage(): Message = message

/**
 * Make properties appropriate for creating a DataSource for unit tests.
 * Defaults configuration of in-memory H2 instance. If 'databaseProvider' system property is set then creates
 * a config from the relevant config file is present in resources folder (used to parametrize test to run against a remote database).
 * Properties retrieval can be parameterized by [configSupplier] and/or [fallBackConfigSupplier] methods
 * which are run with [nodeName] and [nodeNameExtension] parameters.
 *
 * @param nodeName Reflects the "instance" of the in-memory database or database username/schema.
 * Defaults to a random string. Passed to [configSupplier] and [fallBackConfigSupplier] methods.
 * @param nodeNameExtension Provides additional name extension for [configSupplier] and [fallBackConfigSupplier].
 * @param configSupplier Returns [Config] with dataSourceProperties, invoked with [nodeName].
 * Defaults to configuration created when 'databaseProvider' system property is set.
 * @param fallBackConfigSupplier Returns [Config] with dataSourceProperties, invoked with [nodeName] and [nodeNameExtension] parameters.
 * Defaults to configuration of in-memory H2 instance.
 */
fun makeInternalTestDataSourceProperties(nodeName: String? = SecureHash.randomSHA256().toString(),
                                         nodeNameExtension: String? = null,
                                         configSupplier: (String?) -> Config = ::databaseProviderDataSourceConfig,
                                         fallBackConfigSupplier: (String?, String?) -> Config = ::inMemoryH2DataSourceConfig): Properties {
    val config = configSupplier(nodeName)
            .withFallback(fallBackConfigSupplier(nodeName, nodeNameExtension))
            .resolve()

    val props = Properties()
    props.setProperty("dataSourceClassName", config.getString("dataSourceProperties.dataSourceClassName"))
    props.setProperty("dataSource.url", config.getString("dataSourceProperties.dataSource.url"))
    props.setProperty("dataSource.user", config.getString("dataSourceProperties.dataSource.user"))
    props.setProperty("dataSource.password", config.getString("dataSourceProperties.dataSource.password"))
    props.setProperty("autoCommit", "false")
    return props
}

/**
 * Make properties appropriate for creating a Database for unit tests.
 *
 * @param nodeName Reflects the "instance" of the in-memory database or database username/schema.
 * @param configSupplier Returns [Config] with databaseProperties, invoked with [nodeName] parameter.
 */
fun makeTestDatabaseProperties(nodeName: String? = null,
                               configSupplier: (String?) -> Config = ::databaseProviderDataSourceConfig): DatabaseConfig {
    val config = configSupplier(nodeName)
    val transactionIsolationLevel = if (config.hasPath(DatabaseConstants.TRANSACTION_ISOLATION_LEVEL))
        TransactionIsolationLevel.valueOf(config.getString(DatabaseConstants.TRANSACTION_ISOLATION_LEVEL))
    else TransactionIsolationLevel.READ_COMMITTED
    val schema = if (config.hasPath(DatabaseConstants.SCHEMA)) config.getString(DatabaseConstants.SCHEMA) else ""
    return DatabaseConfig(runMigration = true, transactionIsolationLevel = transactionIsolationLevel, schema = schema)
}

/**
 * Reads database and dataSource configuration from a file denoted by 'databaseProvider' system property,
 * overwritten by system properties and defaults to H2 in memory db.
 * @param nodeName Reflects the "instance" of the database username/schema, the value will be used to replace ${custom.nodeOrganizationName} placeholder
 * if the placeholder is present in config.
 */
fun databaseProviderDataSourceConfig(nodeName: String? = null): Config {
    val parseOptions = ConfigParseOptions.defaults()

    val keys = listOf(DatabaseConstants.DATA_SOURCE_URL, DatabaseConstants.DATA_SOURCE_CLASSNAME,
            DatabaseConstants.DATA_SOURCE_USER, DatabaseConstants.DATA_SOURCE_PASSWORD)
    //read overrides from command line (passed by Gradle as system properties)
    val systemConfigOverride = ConfigFactory.parseMap(System.getProperties()
            .filterKeys { (it as String).startsWith(ConfigHelper.CORDA_PROPERTY_PREFIX) }
            .mapKeys { (it.key as String).removePrefix(ConfigHelper.CORDA_PROPERTY_PREFIX) }
            .filterKeys { it in keys })

    //read from db vendor specific configuration file
    val databaseConfig = ConfigFactory.parseResources(System.getProperty("custom.databaseProvider") + ".conf", parseOptions.setAllowMissing(true))
    val fixedOverride = ConfigFactory.parseString("baseDirectory = \"\"")

    //implied property custom.nodeOrganizationName to fill the potential placeholders in db schema/ db user properties
    val nodeOrganizationNameConfig = if (nodeName != null) configOf("custom.nodeOrganizationName" to parseToDbSchemaFriendlyName(nodeName)) else ConfigFactory.empty()

    return systemConfigOverride.withFallback(databaseConfig)
            .withFallback(fixedOverride)
            .withFallback(nodeOrganizationNameConfig)
            .resolve()
}

/**
 * Creates data source configuration for in memory H2 as it would be specified in reference.conf 'datasource' snippet.
 * @param providedNodeName Reflects the "instance" of the database username/schema
 * @param postfix Additional postix added to database "instance" name to add uniqueness when running integration tests.
 */
fun inMemoryH2DataSourceConfig(providedNodeName: String? = null, postfix: String? = null) : Config {
    val nodeName = providedNodeName ?: SecureHash.randomSHA256().toString()
    val h2InstanceName = if (postfix != null) nodeName + "_" + postfix else nodeName

    return ConfigFactory.parseMap(mapOf(
            DatabaseConstants.DATA_SOURCE_CLASSNAME to "org.h2.jdbcx.JdbcDataSource",
            DatabaseConstants.DATA_SOURCE_URL to "jdbc:h2:mem:${h2InstanceName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE",
            DatabaseConstants.DATA_SOURCE_USER to "sa",
            DatabaseConstants.DATA_SOURCE_PASSWORD to ""))
}

fun CordaRPCClient.start(user: User) = start(user.username, user.password)

fun NodeHandle.waitForShutdown(): Observable<Unit> = rpc.waitForShutdown().doAfterTerminate(::stop)

fun CordaRPCOps.waitForShutdown(): Observable<Unit> {
    val completable = AsyncSubject.create<Unit>()
    stateMachinesFeed().updates.subscribe({ }, { error ->
        if (error is ConnectionFailureException) {
            completable.onCompleted()
        } else {
            completable.onError(error)
        }
    })
    return completable
}

fun <T> DriverDSL.withDatabaseConnection(name: CordaX500Name, block: (Connection) -> T): T {
    if (IntegrationTest.isRemoteDatabaseMode()) {
        val databaseConfig = databaseProviderDataSourceConfig(name.toDatabaseSchemaName())
        val dataSource = (DriverManagerDataSource()).also {
            it.setDriverClassName(databaseConfig.getString(DatabaseConstants.DATA_SOURCE_CLASSNAME))
            it.url = databaseConfig.getString(DatabaseConstants.DATA_SOURCE_URL)
            it.username = databaseConfig.getString(DatabaseConstants.DATA_SOURCE_USER)
            it.password = databaseConfig.getString(DatabaseConstants.DATA_SOURCE_PASSWORD)
        }
        val connection: Connection = DataSourceUtils.getConnection(dataSource);
        try {
            return block(connection)
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    } else {
        DriverManager.getConnection("jdbc:h2:file:${baseDirectory(name) / "persistence"}", "sa", "").use { connection ->
            return block(connection)
        }
    }
}

fun DriverDSL.assertCheckpoints(name: CordaX500Name, expected: Long) {
    withDatabaseConnection(name) { connection ->
        connection.createStatement().executeQuery("select count(*) from NODE_CHECKPOINTS").use { rs ->
            rs.next()
            assertThat(rs.getLong(1)).isEqualTo(expected)
        }
    }
}

/**
 * Should only be used by Driver and MockNode.
 */
fun setDriverSerialization(): AutoCloseable? {
    return if (_allEnabledSerializationEnvs.isEmpty()) {
        DriverSerializationEnvironment().enable()
    } else {
        null
    }
}

private class DriverSerializationEnvironment : SerializationEnvironment by createTestSerializationEnv(), AutoCloseable {
    fun enable() = apply { _driverSerializationEnv.set(this) }
    override fun close() {
        _driverSerializationEnv.set(null)
        inVMExecutors.remove(this)
    }
}

/** Add a new entry using the entire remaining bytes of [input] for the entry content. [input] will be closed at the end. */
fun JarOutputStream.addEntry(entry: ZipEntry, input: InputStream) {
    addEntry(entry) { input.use { it.copyTo(this) } }
}

inline fun JarOutputStream.addEntry(entry: ZipEntry, write: () -> Unit) {
    putNextEntry(entry)
    write()
    closeEntry()
}
