package net.corda.nodeapi.internal.cordapp

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.strands.SuspendableCallable
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.SUSPENDING_CALLER
import net.corda.nodeapi.internal.persistence.JavaxPersistenceFixture.Companion.SUSPENDING_REPOSITORY
import org.assertj.core.api.Assertions.assertThat
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.management.ManagementFactory
import java.lang.reflect.Proxy

/**
 * The conversion runs before Quasar's agent sees a class, so Quasar instruments the converted bytes. What it cannot see converted is the
 * jar itself: for a class that is not loaded yet, Quasar reads the method table from the jar, where descriptors still name
 * `javax.persistence`, so a call to such a method through its converted (`jakarta.persistence`) descriptor is not found there. Quasar then
 * assumes the call suspendable (and logs "Method not found in class - assuming suspendable" for the call site), which is the safe direction;
 * once the callee is loaded, Quasar has its converted method table and finds the method. These tests show a fiber suspending and resuming
 * through such calls in both load orders.
 */
class CordappClassLoaderQuasarTest {
    companion object {
        @ClassRule
        @JvmField
        val tempFolder = TemporaryFolder()

        private lateinit var fixture: JavaxPersistenceFixture

        @BeforeClass
        @JvmStatic
        fun compileFixture() {
            fixture = JavaxPersistenceFixture(tempFolder.root.toPath())
        }
    }

    private fun loader() = CordappClassLoader(arrayOf(fixture.jar.toUri().toURL()), CordappClassLoaderQuasarTest::class.java.classLoader)

    /** An entity manager (of the jakarta API, which the converted classes are typed on) that records persists and rejects "y". */
    private fun entityManager(loader: ClassLoader, persisted: MutableList<Any?>): EntityManager {
        return Proxy.newProxyInstance(loader, arrayOf(EntityManager::class.java)) { _, method, args ->
            when (method.name) {
                "persist" -> if (args[0] == "y") throw PersistenceException("rejected") else { persisted += args[0]; null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as EntityManager
    }

    private fun runInFiber(loader: CordappClassLoader, persisted: MutableList<Any?>): String {
        val caller = loader.loadClass(SUSPENDING_CALLER).getConstructor(EntityManager::class.java).newInstance(entityManager(loader, persisted))
        @Suppress("UNCHECKED_CAST")
        return Fiber(caller as SuspendableCallable<String>).start().get()
    }

    @Test(timeout = 300_000)
    fun `the test JVM runs the Quasar agent with instrumentation verification`() {
        // Without the agent, the fiber tests below would prove nothing.
        assertThat(ManagementFactory.getRuntimeMXBean().inputArguments).anyMatch { it.startsWith("-javaagent:") && it.contains("quasar") }
        // Quasar reads the property as true when it is set without a value, which is how the quasar-utils plugin sets it.
        assertThat(System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation")).isIn("", "true")
    }

    @Test(timeout = 300_000)
    fun `converted suspendable methods typed on the entity manager suspend and resume in a fiber when the caller is loaded first`() {
        loader().use { loader ->
            val persisted = mutableListOf<Any?>()
            // Loading (and so instrumenting) the caller first makes Quasar look the callee up in the jar, by the converted descriptor.
            loader.loadClass(SUSPENDING_CALLER)
            assertThat(loader.convertedClassNames).containsExactly(SUSPENDING_CALLER)
            assertThat(runInFiber(loader, persisted)).isEqualTo("persisted x in a fiber; caught PersistenceException for y")
            assertThat(persisted).containsExactly("x")
            assertThat(loader.convertedClassNames).containsExactlyInAnyOrder(SUSPENDING_CALLER, SUSPENDING_REPOSITORY)
        }
    }

    @Test(timeout = 300_000)
    fun `converted suspendable methods typed on the entity manager suspend and resume in a fiber when the callee is loaded first`() {
        loader().use { loader ->
            val persisted = mutableListOf<Any?>()
            loader.loadClass(SUSPENDING_REPOSITORY)
            assertThat(loader.convertedClassNames).containsExactly(SUSPENDING_REPOSITORY)
            assertThat(runInFiber(loader, persisted)).isEqualTo("persisted x in a fiber; caught PersistenceException for y")
            assertThat(persisted).containsExactly("x")
        }
    }
}
