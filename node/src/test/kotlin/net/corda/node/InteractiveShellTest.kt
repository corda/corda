package net.corda.node

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.nhaarman.mockito_kotlin.mock
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.internal.FlowStateMachine
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.shell.InteractiveShell
import net.corda.node.utilities.configureDatabase
import net.corda.testing.DEV_TRUST_ROOT
import net.corda.testing.MEGA_CORP
import net.corda.testing.MEGA_CORP_IDENTITY
import net.corda.testing.node.MockServices
import net.corda.testing.node.MockServices.Companion.makeTestIdentityService
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class InteractiveShellTest {
    @Before
    fun setup() {
        InteractiveShell.database = configureDatabase(MockServices.makeTestDataSourceProperties(), MockServices.makeTestDatabaseProperties(), createIdentityService = ::makeTestIdentityService)
    }

    @After
    fun shutdown() {
        InteractiveShell.database.close()
    }

    @Suppress("UNUSED")
    class FlowA(val a: String) : FlowLogic<String>() {
        constructor(b: Int) : this(b.toString())
        constructor(b: Int, c: String) : this(b.toString() + c)
        constructor(amount: Amount<Currency>) : this(amount.toString())
        constructor(pair: Pair<Amount<Currency>, SecureHash.SHA256>) : this(pair.toString())
        constructor(party: Party) : this(party.name.toString())

        override val progressTracker = ProgressTracker()
        override fun call() = a
    }

    private val ids = InMemoryIdentityService(listOf(MEGA_CORP_IDENTITY), trustRoot = DEV_TRUST_ROOT)
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

    @Test
    fun flowStartWithComplexTypes() = check("amount: £10", "10.00 GBP")

    @Test
    fun flowStartWithNestedTypes() = check(
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
    fun party() = check("party: \"${MEGA_CORP.name}\"", MEGA_CORP.name.toString())

    class DummyFSM(val logic: FlowA) : FlowStateMachine<Any?> by mock()
}
