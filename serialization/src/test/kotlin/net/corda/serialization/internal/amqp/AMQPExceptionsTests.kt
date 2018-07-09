package net.corda.serialization.internal.amqp

import org.assertj.core.api.Assertions
import org.junit.Test
import java.io.NotSerializableException
import kotlin.test.assertEquals

class AMQPExceptionsTests {

    interface Runner {
        fun run()
    }

    data class A<T : Runner>(val a: T, val throws: Boolean = false) : Runner {
        override fun run() = ifThrowsAppend({ javaClass.name }) {
            if (throws) {
                throw AMQPNotSerializableException(A::class.java, "it went bang!")
            } else {
                a.run()
            }
        }
    }

    data class B<T : Runner>(val b: T, val throws: Boolean = false) : Runner {
        override fun run() = ifThrowsAppend({ javaClass.name }) {
            if (throws) {
                throw NotSerializableException(javaClass.name)
            } else {
                b.run()
            }
        }
    }

    data class C<T : Runner>(val c: T, val throws: Boolean = false) : Runner {
        override fun run() = ifThrowsAppend({ javaClass.name }) {
            if (throws) {
                throw NotSerializableException(javaClass.name)
            } else {
                c.run()
            }
        }
    }

    data class END(val throws: Boolean = false) : Runner {
        override fun run() = ifThrowsAppend({ "END" }) {
            if (throws) {
                throw NotSerializableException(javaClass.name)
            }
        }
    }

    data class ENDAMQP(val throws: Boolean = false) : Runner {
        override fun run() {
            if (throws) {
                throw AMQPNotSerializableException(javaClass, "End it all")
            }
        }
    }

    private val aName get() = A::class.java.name
    private val bName get() = B::class.java.name
    private val cName get() = C::class.java.name
    private val eName get() = END::class.java.name
    private val eaName get() = ENDAMQP::class.java.name

    // if the exception is a normal not serializable exception we'll have manipulated the
    // message
    @Test
    fun catchNotSerializable() {
        fun catchAssert(msg: String, f: () -> Unit) {
            Assertions.assertThatThrownBy { f() }
                    .isInstanceOf(NotSerializableException::class.java)
                    .hasMessageContaining(msg)
        }

        catchAssert("$aName -> END") {
            A(END(true)).run()
        }

        catchAssert("$aName -> $aName -> END") {
            A(A(END(true))).run()
        }

        catchAssert("$aName -> $bName -> END") {
            A(B(END(true))).run()
        }

        catchAssert("$aName -> $bName -> $cName -> END") {
            A(B(C(END(true)))).run()
        }
    }

    // However, if its a shiny new AMQPNotSerializable one, we have cool new toys, so
    // lets make sure those are set
    @Test
    fun catchAMQPNotSerializable() {
        fun catchAssert(stack: List<String>, f: () -> Unit): AMQPNotSerializableException {
            try {
                f()
            } catch (e: AMQPNotSerializableException) {
                assertEquals(stack, e.classHierarchy)
                return e
            }

            throw Exception("FAILED")
        }


        catchAssert(listOf(ENDAMQP::class.java.name, aName)) {
            A(ENDAMQP(true)).run()
        }.apply {
            assertEquals(
                    "Serialization failed direction=\"up\", type=\"$eaName\", msg=\"End it all\", " +
                            "ClassChain=\"$aName -> $eaName\"",
                    errorMessage("up"))
        }

        catchAssert(listOf(ENDAMQP::class.java.name, aName, aName)) {
            A(A(ENDAMQP(true))).run()
        }

        catchAssert(listOf(ENDAMQP::class.java.name, bName, aName)) {
            A(B(ENDAMQP(true))).run()
        }

        catchAssert(listOf(ENDAMQP::class.java.name, cName, bName, aName)) {
            A(B(C(ENDAMQP(true)))).run()
        }
    }
}
