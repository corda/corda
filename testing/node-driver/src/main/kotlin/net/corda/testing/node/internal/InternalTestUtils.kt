package net.corda.testing.node.internal

import net.corda.client.rpc.ConnectionFailureException
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.context.InvocationContext
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.FlowStateMachineHandle
import net.corda.core.internal.VisibleForTesting
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.times
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.AttachmentFixup
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._allEnabledSerializationEnvs
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.coretesting.internal.createTestSerializationEnv
import net.corda.coretesting.internal.inVMExecutors
import net.corda.node.services.api.StartedNodeServices
import net.corda.node.services.messaging.Message
import net.corda.node.services.statemachine.Checkpoint
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.NodeHandle
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.testContext
import org.apache.commons.lang3.ClassUtils
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import rx.Observable
import rx.subjects.AsyncSubject
import java.io.InputStream
import java.net.Socket
import java.net.SocketException
import java.nio.file.Path
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.reflect.KClass

private val log = LoggerFactory.getLogger("net.corda.testing.internal.InternalTestUtils")

/**
 * Reference to the finance-contracts CorDapp in this repo. The metadata is taken directly from finance/contracts/build.gradle, including the
 * fact that the jar is signed. If you need an unsigned jar then use `cordappWithPackages("net.corda.finance.contracts")`.
 *
 * You will probably need to use [FINANCE_CORDAPPS] instead to get access to the flows as well.
 */
@JvmField
val FINANCE_CONTRACTS_CORDAPP: ScanPackageTestCordapp = findCordapp("net.corda.finance.contracts")

/**
 * Reference to the finance-workflows CorDapp in this repo. The metadata is taken directly from finance/workflows/build.gradle, including the
 * fact that the jar is signed. If you need an unsigned jar then use `cordappWithPackages("net.corda.finance.flows")`.
 *
 * You will probably need to use [FINANCE_CORDAPPS] instead to get access to the contract classes as well.
 */
@JvmField
val FINANCE_WORKFLOWS_CORDAPP: ScanPackageTestCordapp = findCordapp("net.corda.finance.workflows")

@JvmField
val FINANCE_CORDAPPS: Set<ScanPackageTestCordapp> = setOf(FINANCE_CONTRACTS_CORDAPP, FINANCE_WORKFLOWS_CORDAPP)

/**
 * *Custom* CorDapp containing the contents of the `net.corda.testing.contracts` package, i.e. the dummy contracts. This is not a real CorDapp
 * in the way that [FINANCE_CONTRACTS_CORDAPP] and [FINANCE_WORKFLOWS_CORDAPP] are.
 */
@JvmField
val DUMMY_CONTRACTS_CORDAPP: CustomCordapp = cordappWithPackages("net.corda.testing.contracts")

private const val SECONDS_TO_WAIT_FOR_P2P: Long = 20

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

fun cordappWithFixups(fixups: List<AttachmentFixup>) = CustomCordapp(fixups = fixups)

/**
 * Find the single CorDapp jar on the current classpath which contains the given package. This is a convenience method for
 * [TestCordapp.findCordapp] but returns the internal [ScanPackageTestCordapp].
 */
fun findCordapp(scanPackage: String): ScanPackageTestCordapp = TestCordapp.findCordapp(scanPackage) as ScanPackageTestCordapp

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
        } catch (_: SocketException) {
            null
        }
    }
}

fun nodeMustBeStartedFuture(
        executorService: ScheduledExecutorService,
        logFile: Path,
        listenProcess: Process,
        exception: () -> NodeListenProcessDeathException
): CordaFuture<Unit> {
    val stopPolling = Instant.now().plusSeconds(SECONDS_TO_WAIT_FOR_P2P)
    return poll(executorService, "process $listenProcess is running") {
        if (!listenProcess.isAlive) {
            throw exception()
        }
        when {
            "Running P2PMessaging loop" in logFile.readText() -> {
                Unit
            }
            Instant.now().isAfter(stopPolling) -> {
                // Waited for 20 seconds and the log file did not indicate that the PWP loop is running.
                // This could be because the log is disabled, so lets try to create a client anyway.
                Unit
            }
            else -> {
                null
            }
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
        } catch (_: SocketException) { }
    }
}

/**
 * @param pollInterval the interval running the background task.
 * @param warnCount number of iterations to poll before printing a warning message.
 */
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

class NodeListenProcessDeathException(hostAndPort: NetworkHostAndPort, listenProcess: Process, val causeFromStdError: String) :
    CordaException(
        """
        The node that was expected to start with $hostAndPort has died with status: ${listenProcess.exitValue()}
        Error that caused the process to fail -> $causeFromStdError
        """.trimIndent()
    )

fun <T> StartedNodeServices.startFlow(logic: FlowLogic<T>): FlowStateMachineHandle<T> = startFlow(logic, newContext()).getOrThrow()

fun <T> StartedNodeServices.startFlowWithClientId(clientId: String, logic: FlowLogic<T>): FlowStateMachineHandle<T> =
    startFlow(logic, newContext().copy(clientId = clientId)).getOrThrow()

fun StartedNodeServices.newContext(): InvocationContext = testContext(myInfo.chooseIdentity().name)

fun InMemoryMessagingNetwork.MessageTransfer.getMessage(): Message = message

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

fun DriverDSL.assertUncompletedCheckpoints(name: CordaX500Name, expected: Long) {
    val sqlStatement = "select count(*) from node_checkpoints where status not in (${Checkpoint.FlowStatus.COMPLETED.ordinal})"
    DriverManager.getConnection("jdbc:h2:file:${baseDirectory(name) / "persistence"}", "sa", "").use { connection ->
        connection.createStatement().executeQuery(sqlStatement).use { rs ->
            rs.next()
            assertThat(rs.getLong(1)).isEqualTo(expected)
        }
    }
}

val nodeJvmArgs: List<String> by lazy {
    DriverDSLImpl::class.java.getResourceAsStream("node-jvm-args.txt")!!.use { it.bufferedReader().readLines() }
}

/**
 * Should only be used by Driver and MockNode.
 */
fun setDriverSerialization(classLoader: ClassLoader?): AutoCloseable? {
    return if (_allEnabledSerializationEnvs.isEmpty()) {
        DriverSerializationEnvironment(classLoader).enable()
    } else {
        null
    }
}

fun setDriverSerialization(): AutoCloseable? = setDriverSerialization(null)

private class DriverSerializationEnvironment(classLoader: ClassLoader?)
                  : SerializationEnvironment by createTestSerializationEnv(classLoader), AutoCloseable {
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

fun JarOutputStream.addEntry(entry: ZipEntry) = addEntry(entry) {}

inline fun JarOutputStream.addEntry(entry: ZipEntry, write: () -> Unit) {
    putNextEntry(entry)
    write()
    closeEntry()
}
