package net.corda.serialization.djvm

import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.CordaSerializationTransformEnumDefault
import net.corda.core.serialization.CordaSerializationTransformEnumDefaults
import net.corda.core.serialization.CordaSerializationTransformRename
import net.corda.core.serialization.CordaSerializationTransformRenames
import net.corda.core.serialization.DeprecatedConstructorForDeserialization
import net.corda.djvm.SandboxConfiguration
import net.corda.djvm.SandboxRuntimeContext
import net.corda.djvm.analysis.AnalysisConfiguration
import net.corda.djvm.messages.Severity
import net.corda.djvm.messages.Severity.WARNING
import net.corda.djvm.source.BootstrapClassLoader
import net.corda.djvm.source.UserPathSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.fail
import java.io.File
import java.nio.file.Files.exists
import java.nio.file.Files.isDirectory
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit.MINUTES
import java.util.function.Consumer
import kotlin.concurrent.thread

@Suppress("unused", "MemberVisibilityCanBePrivate")
@Timeout(5, unit = MINUTES)
abstract class TestBase(type: SandboxType) {
    companion object {
        const val SANDBOX_STRING = "sandbox.java.lang.String"

        @JvmField
        val DETERMINISTIC_RT: Path = Paths.get(
            System.getProperty("deterministic-rt.path") ?: fail("deterministic-rt.path property not set"))

        @JvmField
        val TESTING_LIBRARIES: List<Path> = (System.getProperty("sandbox-libraries.path")
                ?: fail("sandbox-libraries.path property not set"))
            .split(File.pathSeparator).map { Paths.get(it) }.filter { exists(it) }

        private lateinit var bootstrapClassLoader: BootstrapClassLoader
        private lateinit var parentConfiguration: SandboxConfiguration

        @BeforeAll
        @JvmStatic
        fun setupClassLoader() {
            bootstrapClassLoader = BootstrapClassLoader(DETERMINISTIC_RT)
            val rootConfiguration = AnalysisConfiguration.createRoot(
                userSource = UserPathSource(emptyList()),
                visibleAnnotations = setOf(
                    CordaSerializable::class.java,
                    CordaSerializationTransformEnumDefault::class.java,
                    CordaSerializationTransformEnumDefaults::class.java,
                    CordaSerializationTransformRename::class.java,
                    CordaSerializationTransformRenames::class.java,
                    ConstructorForDeserialization::class.java,
                    DeprecatedConstructorForDeserialization::class.java
                ),
                bootstrapSource = bootstrapClassLoader
            )
            parentConfiguration = SandboxConfiguration.createFor(
                analysisConfiguration = rootConfiguration,
                profile = null
            )
        }

        @AfterAll
        @JvmStatic
        fun destroyRootContext() {
            bootstrapClassLoader.close()
        }
    }

    val classPaths: List<Path> = when(type) {
        SandboxType.KOTLIN -> TESTING_LIBRARIES
        SandboxType.JAVA -> TESTING_LIBRARIES.filter { isDirectory(it) }
    }

    inline fun sandbox(crossinline action: SandboxRuntimeContext.() -> Unit) {
        sandbox(Consumer { ctx -> action(ctx) })
    }

    fun sandbox(action: Consumer<SandboxRuntimeContext>) {
        sandbox(WARNING, emptySet(), action)
    }

    inline fun sandbox(visibleAnnotations: Set<Class<out Annotation>>, crossinline action: SandboxRuntimeContext.() -> Unit) {
        sandbox(visibleAnnotations, Consumer { ctx -> action(ctx) })
    }

    fun sandbox(
        visibleAnnotations: Set<Class<out Annotation>>,
        action: Consumer<SandboxRuntimeContext>
    ) {
        sandbox(WARNING, visibleAnnotations, action)
    }

    fun sandbox(
        minimumSeverityLevel: Severity,
        visibleAnnotations: Set<Class<out Annotation>>,
        action: Consumer<SandboxRuntimeContext>
    ) {
        var thrownException: Throwable? = null
        thread(start = false) {
            UserPathSource(classPaths).use { userSource ->
                SandboxRuntimeContext(parentConfiguration.createChild(userSource, Consumer {
                    it.setMinimumSeverityLevel(minimumSeverityLevel)
                    it.setVisibleAnnotations(visibleAnnotations)
                })).use(action)
            }
        }.apply {
            uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, ex ->
                thrownException = ex
            }
            start()
            join()
        }
        throw thrownException ?: return
    }
}
