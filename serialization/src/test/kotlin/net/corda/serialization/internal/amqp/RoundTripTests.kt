package net.corda.serialization.internal.amqp

import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.entropyToKeyPair
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.core.serialization.SerializableCalculatedProperty
import net.corda.serialization.internal.amqp.custom.PublicKeySerializer
import net.corda.serialization.internal.amqp.testutils.deserialize
import net.corda.serialization.internal.amqp.testutils.serialize
import net.corda.serialization.internal.amqp.testutils.testDefaultFactoryNoEvolution
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class RoundTripTests {

    @Test(timeout=300_000)
	fun mutableBecomesImmutable() {
        data class C(val l: MutableList<String>)

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        assertThatThrownBy {
            newC.l.add("d")
        }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test(timeout=300_000)
	fun mutableStillMutable() {
        class C(l: MutableList<String>) {
            val l: MutableList<String> = l.toMutableList()
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        newC.l.add("d")
        assertThat(newC.l).containsExactly("a", "b", "c", "d")
    }

    @Test(timeout=300_000)
	fun mutableStillMutable2() {
        data class C(val l: MutableList<String>) {
            @ConstructorForDeserialization
            @Suppress("Unused")
            constructor (l: Collection<String>) : this(l.toMutableList())
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        newC.l.add("d")
        assertThat(newC.l).containsExactly("a", "b", "c", "d")
    }

    @Test(timeout=300_000)
	fun mutableBecomesImmutable4() {
        data class C(val l: List<String>)

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(listOf("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)
        newC.copy(l = (newC.l + "d"))
    }

    @Test(timeout=300_000)
	fun calculatedValues() {
        data class C(val i: Int) {
            @get:SerializableCalculatedProperty
            val squared = i * i
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(2))
        val deserialized = DeserializationInput(factory).deserialize(bytes)
        assertThat(deserialized.squared).isEqualTo(4)
    }

    @Test(timeout=300_000)
	fun calculatedFunction() {
        class C {
            var i: Int = 0
            @SerializableCalculatedProperty
            fun getSquared() = i * i
        }

        val instance = C().apply { i = 2 }
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes)
        assertThat(deserialized.getSquared()).isEqualTo(4)
    }

    interface I {
        @get:SerializableCalculatedProperty
        val squared: Int
    }

    @Test(timeout=300_000)
	fun inheritedCalculatedFunction() {
        class C: I {
            var i: Int = 0
            override val squared get() = i * i
        }

        val instance = C().apply { i = 2 }
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes) as I
        assertThat(deserialized.squared).isEqualTo(4)
    }

    @Test(timeout=300_000)
	fun inheritedCalculatedFunctionIsNotCalculated() {
        class C(override val squared: Int): I

        val instance = C(2)
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes) as I
        assertThat(deserialized.squared).isEqualTo(2)
    }

    data class MembershipState<out T: Any>(val metadata: T): ContractState {
        override val participants: List<AbstractParty>
            get() = emptyList()
    }

    data class OnMembershipChanged(val changedMembership : StateAndRef<MembershipState<Any>>)

    @Test(timeout=300_000)
	fun canSerializeClassesWithUntypedProperties() {
        val data = MembershipState<Any>(mapOf("foo" to "bar"))
        val party = Party(
                CordaX500Name(organisation = "Test Corp", locality = "Madrid", country = "ES"),
                entropyToKeyPair(BigInteger.valueOf(83)).public)
        val transactionState = TransactionState(
                data,
                "foo",
                party
        )
        val ref = StateRef(SecureHash.zeroHash, 0)
        val instance = OnMembershipChanged(StateAndRef(
                transactionState,
                ref
        ))

        val factory = testDefaultFactoryNoEvolution().apply { register(PublicKeySerializer) }
        val bytes = SerializationOutput(factory).serialize(instance)
        val deserialized = DeserializationInput(factory).deserialize(bytes)
        assertEquals(mapOf("foo" to "bar"), deserialized.changedMembership.state.data.metadata)
    }

    interface I2<T> {
        val t: T
    }

    data class C<A, B : A>(override val t: B) : I2<B>

    @Test(timeout=300_000)
	fun recursiveTypeVariableResolution() {
        val factory = testDefaultFactoryNoEvolution()
        val instance = C<Collection<String>, List<String>>(emptyList())

        val bytes = SerializationOutput(factory).serialize(instance)
        DeserializationInput(factory).deserialize(bytes)

        assertEquals(
                """
                C (erased)(t: *): I2<*>
                  t: *
                """.trimIndent(),
                factory.getTypeInformation(instance::class.java).prettyPrint())
    }
}
