package net.corda.commmon.logging

import net.corda.common.logging.walkExceptionCausedByList
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WalkExceptionCausedByListTest(@Suppress("UNUSED_PARAMETER") testTitle: String, private val e: Throwable, private val expectedExceptionSequence: List<Throwable>) {

    private class TestThrowable(val id : Int, cause : Throwable?) : Throwable(cause) {
        override fun toString(): String {
            return "${this.javaClass.simpleName}(${this.id})"
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            val field = Throwable::class.java.getDeclaredField("cause")
            field.isAccessible = true
            return listOf(
                    run {
                        val e = TestThrowable(0, null)
                        arrayOf("Simple exception with no cause", e, listOf(e))
                    },
                    run {
                        var e: TestThrowable? = null
                        val exceptions = (0 until 10).map {
                            e = TestThrowable(it, e)
                            e
                        }
                        arrayOf("Exception with cause list", e!!, exceptions.asReversed())
                    },
                    run {
                        val e = TestThrowable(0, null)
                        field.set(e, e)
                        arrayOf("Exception caused by itself", e, listOf(e))
                    },
                    run {
                        val stack = mutableListOf<TestThrowable>()
                        var e: TestThrowable? = null
                        for(i in 0 until 10) {
                            e = TestThrowable(i, stack.lastOrNull())
                            stack.add(e!!)
                        }
                        field.set(stack[0], stack[4])
                        arrayOf("Exception with loop in cause list", e!!, stack.asReversed())
                    })
        }
    }

    @Test(timeout = 1000)
    fun test() {
        Assert.assertEquals(expectedExceptionSequence, e.walkExceptionCausedByList().asSequence().toList())
    }
}