package net.corda.core.flows.mixins

import com.natpryce.hamkrest.*
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.FlowStateMachine
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.node.internal.StartedNode
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.startFlow
import java.util.*
import kotlin.reflect.KClass

/**
 * Mix this interface into a test to provide functions useful for working with a mock network
 */
interface WithMockNet {

    val mockNet: InternalMockNetwork

    /**
     * Create a node using a randomised version of the given name
     */
    fun makeNode(name: CordaX500Name) = mockNet.createPartyNode(randomise(name))

    /**
     * Randomise a party name to avoid clashes with other tests
     */
    fun randomise(name: CordaX500Name) = name.copy(commonName = "${name.commonName}_${UUID.randomUUID()}")

    /**
     * Run the mock network before proceeding
     */
    fun <T: Any> T.andRunNetwork(): T = apply { mockNet.runNetwork() }

    //region Operations
    /**
     * Sign an initial transaction
     */
    fun StartedNode<*>.signInitialTransaction(build: TransactionBuilder.() -> TransactionBuilder) =
            services.signInitialTransaction(TransactionBuilder(mockNet.defaultNotaryIdentity).build())

    /**
     * Retrieve the sole instance of a state of a particular class from the node's vault
     */
    fun <S: ContractState> StartedNode<*>.getStateFromVault(stateClass: KClass<S>) = database.transaction {
        services.vaultService.queryBy(stateClass.java).states.single()
    }

    /**
     * Start a flow
     */
    fun <T> StartedNode<*>.startFlow(logic: FlowLogic<T>): FlowStateMachine<T> = services.startFlow(logic)

    /**
     * Start a flow and run the network immediately afterwards
     */
    fun <T> StartedNode<*>.startFlowAndRunNetwork(logic: FlowLogic<T>): FlowStateMachine<T> =
            startFlow(logic).andRunNetwork()

    fun StartedNode<*>.createConfidentialIdentity(party: Party) = database.transaction {
        services.keyManagementService.freshKeyAndCert(
                services.myInfo.legalIdentitiesAndCerts.single { it.name == party.name },
                false)
    }

    fun StartedNode<*>.verifyAndRegister(identity: PartyAndCertificate) = database.transaction {
        services.identityService.verifyAndRegisterIdentity(identity)
    }
    //endregion

    //region Matchers
    /**
     * The transaction has the required number of verified signatures
     */
    fun requiredSignatures(count: Int = 1) = object : Matcher<SignedTransaction> {
        override val description: String = "A transaction with valid required signatures"

        override fun invoke(actual: SignedTransaction): MatchResult = try {
            actual.verifyRequiredSignatures()
            has(SignedTransaction::sigs, hasSize(equalTo(count)))(actual)
        } catch (e: Exception) {
            MatchResult.Mismatch("$e")
        }
    }

    /**
     * The exception has the expected error message
     */
    fun errorMessage(expected: String) = has(
        Exception::message,
        equalTo(expected))
    //endregion
}