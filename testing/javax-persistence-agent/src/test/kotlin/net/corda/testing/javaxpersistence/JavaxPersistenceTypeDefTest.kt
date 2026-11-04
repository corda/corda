package net.corda.testing.javaxpersistence

import com.example.javaxtypedef.PersistentJavaxTypeDefState
import net.corda.nodeapi.internal.persistence.CordappConversionException
import net.corda.nodeapi.internal.persistence.JavaxPersistenceTransformer
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.makeTestIdentityService
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * What a developer sees when a CorDapp built against `javax.persistence` uses Hibernate 5's `@TypeDef`, which has no equivalent in
 * Hibernate 7. The agent cannot convert the schema class and the JVM loads it unconverted (ignoring the failed transformer), so the agent
 * reports and records the reason; the node then refuses the CorDapp in its start-up pass over the CorDapp jar, as a real node does, with a
 * message that names the class and the reason, before any session factory is built.
 */
class JavaxPersistenceTypeDefTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val alice = TestIdentity(ALICE_NAME, 70)

    @Test(timeout = 300_000)
    fun `a CorDapp with a Hibernate 5 TypeDef is refused by the node's start-up check, and the agent records the same reason`() {
        val className = PersistentJavaxTypeDefState::class.java.name
        val error = assertFailsWith<CordappConversionException> {
            MockServices.makeTestDatabaseAndMockServices(
                    cordappPackages = listOf("com.example.javaxtypedef"),
                    identityService = makeTestIdentityService(alice.identity),
                    initialIdentity = alice,
                    networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
            )
        }
        assertThat(error.message)
                .contains("cannot be converted for Hibernate 7 (Corda 4.15)")
                .contains("The class $className uses the Hibernate 5 annotation @TypeDef, which no longer exists in Hibernate 7")
                .contains("rebuilt for Corda 4.15")
        // The class was loaded from the test classpath to name it above, so the agent saw it and could not convert it either.
        assertThat(System.getProperty(JavaxPersistenceTransformer.TEST_AGENT_FAILURE_PROPERTY_PREFIX + className))
                .contains("@TypeDef")
                .contains("rebuilt for Corda 4.15")
    }
}
