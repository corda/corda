package net.corda.nodeapi.internal.serialization.amqp

import net.corda.core.serialization.ConstructorForDeserialization
import net.corda.nodeapi.internal.serialization.amqp.testutils.deserialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.serialize
import net.corda.nodeapi.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import org.assertj.core.api.Assertions
import org.junit.Test

class RoundTripTests {
    @Test
    fun mutableBecomesImmutable() {
        data class C(val l : MutableList<String>)
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf ("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        Assertions.assertThatThrownBy {
            newC.l.add("d")
        }.isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun mutableStillMutable() {
        class C {
            val l : MutableList<String>

            @Suppress("Unused")
            constructor (l : MutableList<String>) {
                this.l = l.toMutableList()
            }
        }
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf ("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        newC.l.add("d")
    }

    @Test
    fun mutableStillMutable2() {
        data class C (val l : MutableList<String>){
            @ConstructorForDeserialization
            @Suppress("Unused")
            constructor (l : Collection<String>) : this (l.toMutableList())
        }

        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(mutableListOf ("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)

        newC.l.add("d")
    }

    @Test
    fun mutableBecomesImmutable4() {
        data class C(val l : List<String>)
        val factory = testDefaultFactoryNoEvolution()
        val bytes = SerializationOutput(factory).serialize(C(listOf ("a", "b", "c")))
        val newC = DeserializationInput(factory).deserialize(bytes)
        val newC2 = newC.copy (l = (newC.l + "d"))
    }
}