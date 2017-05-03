package net.corda.node

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.contracts.Amount
import net.corda.core.crypto.Party
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowInitiator
import net.corda.core.crypto.X509Utilities
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStateMachine
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.core.utilities.UntrustworthyData
import net.corda.jackson.JacksonSupport
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.shell.InteractiveShell
import net.corda.testing.MEGA_CORP
import org.junit.Test
import org.slf4j.Logger
import java.util.*
import kotlin.test.assertEquals

class InteractiveShellTest {
    @Suppress("UNUSED")
    class FlowA(val a: String) : FlowLogic<String>() {
        constructor(b: Int) : this(b.toString())
        constructor(b: Int, c: String) : this(b.toString() + c)
        constructor(amount: Amount<Currency>) : this(amount.toString())
        constructor(pair: Pair<Amount<Currency>, SecureHash.SHA256>) : this(pair.toString())
        constructor(party: Party) : this(party.name.toString())
        override fun call() = a
    }

    private val someCorpLegalName = MEGA_CORP.name
    private val ids = InMemoryIdentityService().apply { registerIdentity(Party(someCorpLegalName, DUMMY_PUBKEY_1)) }
    private val om = JacksonSupport.createInMemoryMapper(ids, YAMLFactory())

    private fun check(input: String, expected: String) {
        var output: DummyFSM? = null
        InteractiveShell.runFlowFromString({ DummyFSM(it as FlowA).apply { output = this } }, input, FlowA::class.java, om)
        assertEquals(expected, output!!.logic.a, input)
    }

    @Test
    fun flowStartSimple() {
        check("a: Hi there", "Hi there")
        check("b: 12", "12")
        check("b: 12, c: Yo", "12Yo")
    }

    @Test fun flowStartWithComplexTypes() = check("amount: £10", "10.00 GBP")

    @Test fun flowStartWithNestedTypes() = check(
            "pair: { first: $100.12, second: df489807f81c8c8829e509e1bcb92e6692b9dd9d624b7456435cb2f51dc82587 }",
            "($100.12, df489807f81c8c8829e509e1bcb92e6692b9dd9d624b7456435cb2f51dc82587)"
    )

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowStartNoArgs() = check("", "")

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowMissingParam() = check("c: Yo", "")

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowTooManyParams() = check("b: 12, c: Yo, d: Bar", "")

    @Test
    fun party() = check("party: \"${someCorpLegalName}\"", someCorpLegalName.toString())

    class DummyFSM(val logic: FlowA) : FlowStateMachine<Any?> {
        override fun <T : Any> sendAndReceive(receiveType: Class<T>, otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>): UntrustworthyData<T> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun <T : Any> receive(receiveType: Class<T>, otherParty: Party, sessionFlow: FlowLogic<*>): UntrustworthyData<T> {
            throw UnsupportedOperationException("not implemented")
        }

        override fun send(otherParty: Party, payload: Any, sessionFlow: FlowLogic<*>) {
            throw UnsupportedOperationException("not implemented")
        }

        override fun waitForLedgerCommit(hash: SecureHash, sessionFlow: FlowLogic<*>): SignedTransaction {
            throw UnsupportedOperationException("not implemented")
        }

        override fun createHandle(hasProgress: Boolean): FlowProgressHandle<Any?> = throw UnsupportedOperationException("not implemented")

        override val serviceHub: ServiceHub
            get() = throw UnsupportedOperationException()
        override val logger: Logger
            get() = throw UnsupportedOperationException()
        override val id: StateMachineRunId
            get() = throw UnsupportedOperationException()
        override val resultFuture: ListenableFuture<Any?>
            get() = throw UnsupportedOperationException()
        override val flowInitiator: FlowInitiator
            get() = throw UnsupportedOperationException()
    }
}