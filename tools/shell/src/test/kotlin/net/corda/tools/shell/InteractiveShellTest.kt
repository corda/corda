package net.corda.tools.shell

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.FlowProgressHandleImpl
import net.corda.core.utilities.ProgressTracker
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.core.TestIdentity
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals

class InteractiveShellTest {
    companion object {
        private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    }

    @Suppress("UNUSED")
    class FlowA(val a: String) : FlowLogic<String>() {
        constructor(b: Int?) : this(b.toString())
        constructor(b: Int?, c: String) : this(b.toString() + c)
        constructor(amount: Amount<Currency>) : this(amount.toString())
        constructor(pair: Pair<Amount<Currency>, SecureHash.SHA256>) : this(pair.toString())
        constructor(party: Party) : this(party.name.toString())

        override val progressTracker = ProgressTracker()
        override fun call() = a
    }

    private val ids = InMemoryIdentityService(listOf(megaCorp.identity), DEV_ROOT_CA.certificate)
    @Suppress("DEPRECATION")
    private val om = JacksonSupport.createInMemoryMapper(ids, YAMLFactory())

    private fun check(input: String, expected: String) {
        var output: String? = null
        InteractiveShell.runFlowFromString({ clazz, args ->

            val instance = clazz.getConstructor(*args.map { it!!::class.java }.toTypedArray()).newInstance(*args) as FlowA
            output = instance.a
            val future = openFuture<String>()
            future.set("ABC")
            FlowProgressHandleImpl(StateMachineRunId.createRandom(), future, Observable.just("Some string"))
        }, input, FlowA::class.java, om)
        assertEquals(expected, output!!, input)
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
    fun party() = check("party: \"${megaCorp.name}\"", megaCorp.name.toString())
}
