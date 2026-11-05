package net.corda.node.services.persistence

import com.example.javaxcordapp.PersistentJavaxTestState
import net.corda.nodeapi.internal.persistence.HibernateConfigException
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import java.lang.management.ManagementFactory
import kotlin.test.assertFailsWith

/**
 * In-process (MockServices) use of a CorDapp built against `javax.persistence` whose classes are on the test classpath
 * (`com.example.javaxcordapp`, built for Corda 4.12) in a test JVM that does not run the javax persistence test agent: the CorDapp class
 * loader cannot convert classes the test classpath serves first, so the node fails fast with a message that names the agent. The tests
 * that run with the agent are in `testing/javax-persistence-agent`.
 */
class JavaxPersistenceWithoutAgentTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val alice = TestIdentity(ALICE_NAME, 70)
    private val notary = TestIdentity(DUMMY_NOTARY_NAME, 20)

    @Test(timeout = 300_000)
    fun `the test JVM does not run the javax persistence test agent`() {
        assertThat(ManagementFactory.getRuntimeMXBean().inputArguments).noneMatch { it.startsWith("-javaagent:") && it.contains("javax-persistence") }
        // The property the agent sets once installed (JavaxPersistenceTransformer.AGENT_INSTALLED_PROPERTY).
        assertThat(System.getProperty("net.corda.javaxpersistence.agent")).isNull()
    }

    @Test(timeout = 300_000)
    fun `a javax-only CorDapp on the test classpath is refused with a message that names the agent`() {
        val error = assertFailsWith<HibernateConfigException> {
            MockServices.makeTestDatabaseAndMockServices(
                    cordappPackages = listOf("com.example.javaxcordapp"),
                    identityService = makeTestIdentityService(alice.identity, notary.identity),
                    initialIdentity = alice,
                    networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
            )
        }
        assertThat(error.message)
                .contains("The class ${PersistentJavaxTestState::class.java.name}")
                .contains("was not loaded through the node's CorDapp class loader")
                .contains("MockNetwork, MockServices or in-process driver test")
                .contains("-javaagent:corda-javax-persistence-test-agent.jar")
    }
}
