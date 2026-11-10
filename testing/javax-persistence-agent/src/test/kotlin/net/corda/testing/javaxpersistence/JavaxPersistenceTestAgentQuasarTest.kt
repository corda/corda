package net.corda.testing.javaxpersistence

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
import java.net.URLClassLoader

/**
 * The agent next to Quasar's: a converted class with `@Suspendable` methods whose descriptors name the `javax.persistence` API (before
 * conversion) suspends and resumes in a fiber, with Quasar's instrumentation verification on, in both load orders. The agent's
 * transformer is registered as retransformation-incapable, which the JVM runs before Quasar's retransformation-capable one whatever
 * the order of the agents, so Quasar instruments the converted bytes; the documented wiring lists the agent first all the same. The
 * loader-path equivalent is `CordappClassLoaderQuasarTest` in `node-api`.
 */
class JavaxPersistenceTestAgentQuasarTest {
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

    private fun loader() = URLClassLoader(arrayOf(fixture.jar.toUri().toURL()), JavaxPersistenceTestAgentQuasarTest::class.java.classLoader)

    /** An entity manager (of the jakarta API, which the converted classes are typed on) that records persists and rejects "y". */
    private fun entityManager(loader: ClassLoader, persisted: MutableList<Any?>): EntityManager {
        return Proxy.newProxyInstance(loader, arrayOf(EntityManager::class.java)) { _, method, args ->
            when (method.name) {
                "persist" -> if (args[0] == "y") throw PersistenceException("rejected") else { persisted += args[0]; null }
                else -> throw UnsupportedOperationException(method.name)
            }
        } as EntityManager
    }

    private fun runInFiber(loader: ClassLoader, persisted: MutableList<Any?>): String {
        val caller = loader.loadClass(SUSPENDING_CALLER).getConstructor(EntityManager::class.java).newInstance(entityManager(loader, persisted))
        @Suppress("UNCHECKED_CAST")
        return Fiber(caller as SuspendableCallable<String>).start().get()
    }

    @Test(timeout = 300_000)
    fun `the test JVM runs the agent before Quasar's with instrumentation verification on`() {
        // Other agents (code coverage) may be present too; what matters is that both are, and their order.
        val agents = ManagementFactory.getRuntimeMXBean().inputArguments.filter { it.startsWith("-javaagent:") }
        val javaxPersistenceAgent = agents.indexOfFirst { it.contains("corda-javax-persistence-test-agent") }
        val quasarAgent = agents.indexOfFirst { it.contains("quasar") }
        assertThat(javaxPersistenceAgent).describedAs("javax persistence agent in $agents").isNotNegative()
        assertThat(quasarAgent).describedAs("Quasar agent in $agents").isNotNegative()
        assertThat(javaxPersistenceAgent).describedAs("javax persistence agent before Quasar's in $agents").isLessThan(quasarAgent)
        // Quasar reads the property as true when it is set without a value, which is how the quasar-utils plugin sets it.
        assertThat(System.getProperty("co.paralleluniverse.fibers.verifyInstrumentation")).isIn("", "true")
    }

    @Test(timeout = 300_000)
    fun `converted suspendable methods typed on the entity manager suspend and resume in a fiber when the caller is loaded first`() {
        loader().use { loader ->
            val persisted = mutableListOf<Any?>()
            // Loading (and so instrumenting) the caller first makes Quasar look the callee up in the jar, by the converted descriptor.
            loader.loadClass(SUSPENDING_CALLER)
            assertThat(runInFiber(loader, persisted)).isEqualTo("persisted x in a fiber; caught PersistenceException for y")
            assertThat(persisted).containsExactly("x")
        }
    }

    @Test(timeout = 300_000)
    fun `converted suspendable methods typed on the entity manager suspend and resume in a fiber when the callee is loaded first`() {
        loader().use { loader ->
            val persisted = mutableListOf<Any?>()
            loader.loadClass(SUSPENDING_REPOSITORY)
            assertThat(runInFiber(loader, persisted)).isEqualTo("persisted x in a fiber; caught PersistenceException for y")
            assertThat(persisted).containsExactly("x")
        }
    }
}
