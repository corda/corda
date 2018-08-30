/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.tools.shell

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.internal.ToStringSerialize
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
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.DEV_ROOT_CA
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InteractiveShellTest {
    companion object {
        private val megaCorp = TestIdentity(CordaX500Name("MegaCorp", "London", "GB"))
    }

    @Suppress("UNUSED")
    class FlowA(val a: String) : FlowLogic<String>() {
        constructor(b: Int) : this(b.toString())
        constructor(b: Int?, c: String) : this(b.toString() + c)
        constructor(amount: Amount<Currency>) : this(amount.toString())
        constructor(pair: Pair<Amount<Currency>, SecureHash.SHA256>) : this(pair.toString())
        constructor(party: Party) : this(party.name.toString())
        constructor(b: Int?, amount: Amount<UserValue>) : this("${(b ?: 0) + amount.quantity} ${amount.token}")
        constructor(b: Array<String>) : this(b.joinToString("+"))
        constructor(amounts: Array<Amount<UserValue>>) : this(amounts.map(Amount<UserValue>::toString).joinToString("++"))

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
        input = "pair: { first: $100.12, second: df489807f81c8c8829e509e1bcb92e6692b9dd9d624b7456435cb2f51dc82587 }",
        expected = "(100.12 USD, DF489807F81C8C8829E509E1BCB92E6692B9DD9D624B7456435CB2F51DC82587)"
    )

    @Test
    fun flowStartWithArrayType() = check(
        input = "b: [ One, Two, Three, Four ]",
        expected = "One+Two+Three+Four"
    )

    @Test
    fun flowStartWithUserAmount() = check(
        input = """b: 500, amount: { "quantity": 10001, "token":{ "label": "of value" } }""",
        expected = "10501 of value"
    )

    @Test
    fun flowStartWithArrayOfNestedTypes() = check(
        input = """amounts: [ { "quantity": 10, "token": { "label": "(1)" } }, { "quantity": 200, "token": { "label": "(2)" } } ]""",
        expected = "10 (1)++200 (2)"
    )

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowStartNoArgs() = check("", "")

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowMissingParam() = check("c: Yo", "")

    @Test(expected = InteractiveShell.NoApplicableConstructor::class)
    fun flowTooManyParams() = check("b: 12, c: Yo, d: Bar", "")

    @Test
    fun niceTypeNamesInErrors() {
        val e = assertFailsWith<InteractiveShell.NoApplicableConstructor> {
            check("", expected = "")
        }
        val correct = setOf(
                "[amounts: Amount<InteractiveShellTest.UserValue>[]]: missing parameter amounts",
                "[amount: Amount<Currency>]: missing parameter amount",
                "[pair: Pair<Amount<Currency>, SecureHash.SHA256>]: missing parameter pair",
                "[party: Party]: missing parameter party",
                "[b: Integer, amount: Amount<InteractiveShellTest.UserValue>]: missing parameter b",
                "[b: String[]]: missing parameter b",
                "[b: Integer, c: String]: missing parameter b",
                "[a: String]: missing parameter a",
                "[b: int]: missing parameter b"
        )
        val errors = e.errors.toHashSet()
        errors.removeAll(correct)
        assert(errors.isEmpty()) { errors.joinToString(", ") }
    }

    @Test
    fun party() = check("party: \"${megaCorp.name}\"", megaCorp.name.toString())

    @ToStringSerialize
    data class UserValue(@JsonProperty("label") val label: String) {
        override fun toString() = label
    }
}
