package net.corda.testing.driver.junit.jupiter

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.NotaryHandle
import net.corda.testing.driver.driver
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport.findAnnotatedMethods
import org.junit.platform.commons.support.HierarchyTraversalMode
import rx.exceptions.OnCompletedFailedException
import rx.exceptions.OnErrorFailedException
import rx.exceptions.OnErrorNotImplementedException
import rx.exceptions.OnErrorThrowable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Implementation of a Junit Jupiter Extension to
 * start Corda nodes for integration tests.
 * @constructor
 */
class CordaDriverJunitJupiterImpl(
        override val parametersForNodes: List<NodeParameters>,
        override val driverParameters: DriverParameters = DriverParameters(),
        override val maxTimeForNodeToBeStarted: Duration? = null,
        override val useTempDriverDirectory: Boolean = false
) : CordaDriverJunitJupiter {

    /**
     * Construct [CordaDriverJunitJupiterImpl] from a [CordaDriverJunitJupiterConfig] instance.
     */
    constructor(cordaDriverJunitJupiterConfig: CordaDriverJunitJupiterConfig) : this(
            cordaDriverJunitJupiterConfig.parametersForNodes,
            cordaDriverJunitJupiterConfig.driverParameters,
            cordaDriverJunitJupiterConfig.maxTimeForNodeToBeStarted,
            cordaDriverJunitJupiterConfig.useTempDriverDirectory
    )

    init {
        catchAndRethrowTestSetupException<IllegalArgumentException> {
            require(parametersForNodes.isNotEmpty()) {
                "At least one `NodeParameters` instance must be provided to the Corda driver via `parametersForNodes`."
            }
            parametersForNodes.forEach { nodeParameters ->
                requireNotNull(nodeParameters.providedName) { "Each `NodeParameters` instance must explicitly specify a non-null value for `providedName`." }
            }
        }
    }

    override val cordaX500Names: List<CordaX500Name> = parametersForNodes.map { it.providedName!! }

    companion object {
        /**
         * Builder class to build/configure a Junit Jupiter Corda driver extension.
         */
        class Builder {
            /**
             * List of [NodeParameters] elements, where each of them
             * configures a single Corda node.
             */
            private var parametersForNodes: MutableList<NodeParameters> = mutableListOf()

            /**
             * Driver parameters such as Cordapps to load,
             * network parameters ...
             */
            private var driverParameters: DriverParameters = DriverParameters()

            /**
             * Maximum time to wait for the Corda future of
             * the Corda start node process to finish until
             * an error is thrown. Null value allowed which
             * is treated as no time limitation.
             */
            private var maxTimeForNodeToBeStarted: Duration? = null

            /**
             * If true, driver files are stored in a temporary
             * directory which will be deleted after the test
             * has been executed.
             */
            var useTempDriverDirectory: Boolean = false

            /**
             * configure multiple nodes at once by providing
             * a list of node parameters elements, where each element
             * configure one node.
             */
            fun configureNodes(nodeConfigurationsToAdd: List<NodeParameters>) {
                parametersForNodes.addAll(nodeConfigurationsToAdd)
            }

            /**
             * provide the node parameters for a single node. This node
             * configuration is appended to previously provided node parameters.
             */
            fun addNodeConfiguration(nodeConfigurationToAdd: NodeParameters) {
                parametersForNodes.add(nodeConfigurationToAdd)
            }

            /**
             * Set Corda driver parameters.
             */
            fun setDriverParameters(driverParametersToSet: DriverParameters) {
                driverParameters = driverParametersToSet
            }

            /**
             * Set maximum time to wait for the Corda future of
             * the Corda start node process to finish until
             * an error is thrown. Null value is allowed which
             * will be treated as no time limitation.
             */
            fun setMaxTimeForNodeToBeStarted(maxTimeForNodeToBeStartedToSet: Duration) {
                maxTimeForNodeToBeStarted = maxTimeForNodeToBeStartedToSet
            }

            /**
             * Build the Corda driver with the provided configuration.
             */
            fun build(): CordaDriverJunitJupiterImpl {
                return CordaDriverJunitJupiterImpl(
                        parametersForNodes = parametersForNodes,
                        driverParameters = driverParameters,
                        maxTimeForNodeToBeStarted = maxTimeForNodeToBeStarted,
                        useTempDriverDirectory = useTempDriverDirectory
                )
            }
        }

        fun <T : Any> catchAndRethrowExceptions(block: () -> T): T {
            return try {
                block()
            } catch (ex: Exception) {
                when (ex) {
                    is InvocationTargetException -> throw ex.targetException
                    is OnCompletedFailedException,
                    is OnErrorFailedException,
                    is OnErrorThrowable,
                    is OnErrorNotImplementedException -> throw ex.cause ?: NullPointerException(ex.localizedMessage)

                    else -> throw ex
                }
            }
        }
    }

    private val driverDirectory: Path = if (useTempDriverDirectory)
        Files.createTempDirectory(null)
    else driverParameters.driverDirectory

    private var cordaNodeHandles: List<NodeHandle>? = null
    private var cordaDefaultNotaryHandle: NotaryHandle? = null

    override fun interceptTestMethod(
            invocation: InvocationInterceptor.Invocation<Void>,
            invocationContext: ReflectiveInvocationContext<Method>,
            extensionContext: ExtensionContext
    ) {

        try {
            val testClass = extensionContext.requiredTestClass
            val testInstance = extensionContext.requiredTestInstance

            fun <T : Annotation> invokeAnnotatedMethods(
                    annotationType: Class<out T>
            ) {

                findAnnotatedMethods(
                        testClass,
                        annotationType,
                        HierarchyTraversalMode.TOP_DOWN
                ).forEach { method ->
                    catchAndRethrowExceptions {
                        method.invoke(
                                testInstance
                        )
                    }
                }
            }

            driver(
                    defaultParameters = driverParameters.copy(driverDirectory = driverDirectory)
            ) {

                invokeAnnotatedMethods(BeforeNodeInitInCordaDriverContext::class.java)

                cordaNodeHandles = catchAndRethrowExceptions {
                    parametersForNodes.map { nodeParameters ->
                        startNode(nodeParameters).getOrThrow(maxTimeForNodeToBeStarted)
                    }
                }

                cordaDefaultNotaryHandle = this.defaultNotaryHandle

                invokeAnnotatedMethods(BeforeEachTestInCordaDriverContext::class.java)

                catchAndRethrowExceptions {
                    invocation.proceed()
                }

                invokeAnnotatedMethods(AfterEachTestInCordaDriverContext::class.java)

            }
        } finally {

            // temporary driver directory should always be deleted no matter
            // whether the test succeeded or failed.

            if (useTempDriverDirectory) {
                Files.walk(driverDirectory)
                        // The reverse order places the directory itself
                        // at the end ->
                        // the directory is deleted as last element
                        // ensuring it is empty at the time of deletion
                        .sorted(Comparator.reverseOrder())
                        .forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    override fun getCordaNodeHandlesOrNull(): List<NodeHandle>? = cordaNodeHandles

    override fun getCordaNodeHandlesOrThrow(): List<NodeHandle> =
            cordaNodeHandles ?: throw IllegalArgumentException("The parties' Corda node handles have not been initialized.")

    override fun getDefaultCordaNotaryHandleOrNull(): NotaryHandle? = cordaDefaultNotaryHandle

    override fun getDefaultCordaNotaryHandleOrThrow(): NotaryHandle =
            cordaDefaultNotaryHandle ?: throw IllegalArgumentException("The default Corda notary handle is not available.")
}
