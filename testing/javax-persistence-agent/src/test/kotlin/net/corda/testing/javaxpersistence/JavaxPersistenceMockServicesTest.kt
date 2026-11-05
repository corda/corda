package net.corda.testing.javaxpersistence

import com.example.javaxcordapp.JavaxTestContract
import com.example.javaxcordapp.JavaxTestState
import com.example.javaxcordapp.PersistentJavaxTestState
import com.example.javaxtest.findJavaxTestStateLabels
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder
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
import java.util.UUID

/**
 * MockServices with a CorDapp built against `javax.persistence` (`com.example.javaxcordapp`, built for Corda 4.12) on the test classpath,
 * in a test JVM that runs the javax persistence test agent: the node's check passes because the loaded classes carry the
 * `jakarta.persistence` annotations, and the vault persists and queries the CorDapp's states through the converted schema. The same
 * test without the agent, refused by the node, is `JavaxPersistenceWithoutAgentTest` in the `node` module.
 */
class JavaxPersistenceMockServicesTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val alice = TestIdentity(ALICE_NAME, 70)
    private val notary = TestIdentity(DUMMY_NOTARY_NAME, 20)

    @Test(timeout = 300_000)
    fun `a javax-only CorDapp on the test classpath persists and queries through the vault`() {
        val (database, services) = MockServices.makeTestDatabaseAndMockServices(
                cordappPackages = listOf("com.example.javaxcordapp"),
                identityService = makeTestIdentityService(alice.identity, notary.identity),
                initialIdentity = alice,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
        )
        database.use {
            val state = JavaxTestState("archived", UUID.randomUUID(), listOf(alice.party))
            database.transaction {
                val stx = services.signInitialTransaction(TransactionBuilder(notary.party)
                        .addOutputState(state)
                        .addCommand(JavaxTestContract.Create(), alice.publicKey))
                services.recordTransactions(stx)
            }
            // A query on the label, and one on the UUID column mapped with the substituted Hibernate 5 type.
            val byLabel = VaultCustomQueryCriteria(builder { PersistentJavaxTestState::label.equal("archived") })
            val byRef = VaultCustomQueryCriteria(builder { PersistentJavaxTestState::ref.equal(state.ref) })
            database.transaction {
                assertThat(services.vaultService.queryBy<JavaxTestState>(byLabel).states.map { it.state.data }).containsExactly(state)
                assertThat(services.vaultService.queryBy<JavaxTestState>(byRef).states.map { it.state.data }).containsExactly(state)
                // Test code typed on the javax.persistence API (a withEntityManager block in a test) is converted too.
                assertThat(services.findJavaxTestStateLabels()).containsExactly("archived")
            }

            val annotationNames = PersistentJavaxTestState::class.java.annotations.map { it.annotationClass.java.name }
            assertThat(annotationNames).contains("jakarta.persistence.Entity", "jakarta.persistence.Table")
        }
    }
}
