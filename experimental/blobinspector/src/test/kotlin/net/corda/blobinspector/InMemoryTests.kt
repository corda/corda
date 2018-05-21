package net.corda.blobinspector

import net.corda.core.serialization.SerializedBytes
import net.corda.serialization.internal.AllWhitelist
import net.corda.serialization.internal.amqp.SerializationOutput
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import org.junit.Test


class InMemoryTests {
    private val factory = SerializerFactory(AllWhitelist, ClassLoader.getSystemClassLoader())

    private fun inspect (b: SerializedBytes<*>) {
        BlobHandler.make(
                InMemoryConfig(Mode.inMem).apply { blob = b; data = true}
        ).apply {
            inspectBlob(config, getBytes())
        }
    }

    @Test
    fun test1() {
        data class C (val a: Int, val b: Long, val c: String)
        inspect (SerializationOutput(factory).serialize(C(100, 567L, "this is a test"), AMQP_P2P_CONTEXT))
    }

    @Test
    fun test2() {
        data class C (val i: Int, val c: C?)
        inspect (SerializationOutput(factory).serialize(C(1, C(2, C(3, C(4, null)))), AMQP_P2P_CONTEXT))
    }

    @Test
    fun test3() {
        data class C (val a: IntArray, val b: Array<String>)

        val a = IntArray(10) { i -> i }
        val c = C(a, arrayOf("aaa", "bbb", "ccc"))

        inspect (SerializationOutput(factory).serialize(c, AMQP_P2P_CONTEXT))
    }

    @Test
    fun test4() {
        data class Elem(val e1: Long, val e2: String)
        data class Wrapper (val name: String, val elementes: List<Elem>)

        inspect (SerializationOutput(factory).serialize(
                Wrapper("Outer Class",
                        listOf(
                                Elem(1L, "First element"),
                                Elem(2L, "Second element"),
                                Elem(3L, "Third element")
                        )), AMQP_P2P_CONTEXT))
    }

    @Test
    fun test4b() {
        data class Elem(val e1: Long, val e2: String)
        data class Wrapper (val name: String, val elementes: List<List<Elem>>)

        inspect (SerializationOutput(factory).serialize(
                Wrapper("Outer Class",
                        listOf (
                                listOf(
                                        Elem(1L, "First element"),
                                        Elem(2L, "Second element"),
                                        Elem(3L, "Third element")
                                ),
                                listOf(
                                        Elem(4L, "Fourth element"),
                                        Elem(5L, "Fifth element"),
                                        Elem(6L, "Sixth element")
                                )
                        )), AMQP_P2P_CONTEXT))
    }

    @Test
    fun test5() {
        data class C (val a: Map<String, String>)

        inspect (SerializationOutput(factory).serialize(
                C(mapOf(
                        "a" to "a a a",
                        "b" to "b b b",
                        "c" to "c c c")),
                AMQP_P2P_CONTEXT
        ))
    }
}