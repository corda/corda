package net.corda.testing.javaxpersistence

import com.example.javaxtypedef.PersistentJavaxTypeDefState
import net.corda.common.javaxpersistence.JavaxPersistenceTransformer
import net.corda.nodeapi.internal.persistence.HibernateConfigException
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
 * Hibernate 7: the agent cannot convert the schema class, the JVM loads it unconverted (ignoring the failed transformer), and the
 * node's start-up check names the class and the agent's reason instead of its generic message.
 */
class JavaxPersistenceTypeDefTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val alice = TestIdentity(ALICE_NAME, 70)

    @Test(timeout = 300_000)
    fun `a CorDapp with a Hibernate 5 TypeDef is refused with the reason the agent recorded`() {
        val error = assertFailsWith<HibernateConfigException> {
            MockServices.makeTestDatabaseAndMockServices(
                    cordappPackages = listOf("com.example.javaxtypedef"),
                    identityService = makeTestIdentityService(alice.identity),
                    initialIdentity = alice,
                    networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
            )
        }
        val className = PersistentJavaxTypeDefState::class.java.name
        assertThat(System.getProperty(JavaxPersistenceTransformer.AGENT_FAILURE_PROPERTY_PREFIX + className)).contains("@TypeDef")
        assertThat(error.message)
                .contains("The class $className")
                .contains("the javax persistence test agent could not convert it for Hibernate 7 (Corda 4.15)")
                .contains("uses the Hibernate 5 annotation @TypeDef, which no longer exists in Hibernate 7")
                .contains("rebuilt for Corda 4.15")
                .doesNotContain("was not loaded through the node's CorDapp class loader")
    }
}
