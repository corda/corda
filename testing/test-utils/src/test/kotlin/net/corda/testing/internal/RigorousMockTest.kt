package net.corda.testing.internal

import org.assertj.core.api.Assertions.catchThrowable
import org.hamcrest.Matchers.isA
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.Closeable
import java.io.InputStream
import java.io.Serializable
import java.util.stream.Stream
import kotlin.test.*

private interface MyInterface {
    fun abstractFun(): Int
    fun kotlinDefaultFun() = 5
}

private abstract class MyAbstract : MyInterface
private open class MyImpl : MyInterface {
    override fun abstractFun() = 4
    open fun openFun() = 6
    fun finalFun() = 7
    override fun toString() = "8"
}

private interface MySpectator {
    fun sideEffect()
    fun noClearDefault(): Int
    fun collaborator(arg: Int): MySpectator
}

class RigorousMockTest {
    @Test
    fun `toString has a reliable default answer in all cases`() {
        Stream.of<(Class<out Any>) -> Any>(::spectator, ::rigorousMock, ::participant).forEach { profile ->
            Stream.of(MyInterface::class, MyAbstract::class, MyImpl::class).forEach { type ->
                val mock = profile(type.java)
                assertEquals("${mock.javaClass.simpleName}@${Integer.toHexString(mock.hashCode())}", mock.toString())
            }
        }
    }

    @Test
    fun `callRealMethod is preferred by rigorousMock`() {
        rigorousMock<MyInterface>().let { m ->
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.abstractFun() }.javaClass)
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.kotlinDefaultFun() }.javaClass)
        }
        rigorousMock<MyAbstract>().let { m ->
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.abstractFun() }.javaClass)
            assertEquals(5, m.kotlinDefaultFun())
        }
        rigorousMock<MyImpl>().let { m ->
            assertEquals(4, m.abstractFun())
            assertEquals(5, m.kotlinDefaultFun())
            assertEquals(6, m.openFun())
            assertEquals(7, m.finalFun())
        }
    }

    @Test
    fun `throw exception is preferred by participant`() {
        participant<MyInterface>().let { m ->
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.abstractFun() }.javaClass)
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.kotlinDefaultFun() }.javaClass)
        }
        participant<MyAbstract>().let { m ->
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.abstractFun() }.javaClass)
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.kotlinDefaultFun() }.javaClass) // Broken in older Mockito.
        }
        participant<MyImpl>().let { m ->
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.abstractFun() }.javaClass)
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.kotlinDefaultFun() }.javaClass)
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.openFun() }.javaClass)
            assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.finalFun() }.javaClass)
        }
    }

    @Test
    fun `doing nothing is preferred by spectator`() {
        val mock: MySpectator = spectator()
        mock.sideEffect()
        assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { mock.noClearDefault() }.javaClass)
        val collaborator = mock.collaborator(1)
        assertNotSame(mock, collaborator)
        assertSame(collaborator, mock.collaborator(1))
        assertNotSame(collaborator, mock.collaborator(2))
        collaborator.sideEffect()
        assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { collaborator.noClearDefault() }.javaClass)
    }

    private open class AB<out A, out B> {
        val a: A get() = throw UnsupportedOperationException()
        val b: B get() = throw UnsupportedOperationException()
    }

    private open class CD<out C, out D> : AB<D, C>()
    private class CDImpl : CD<Runnable, String>()

    @Test
    fun `method return type resolution works`() {
        val m = spectator<CDImpl>()
        assertThat(m.b, isA(Runnable::class.java))
        assertSame<Any>(UndefinedMockBehaviorException::class.java, catchThrowable { m.a }.javaClass) // Can't mock String.
    }

    private interface RS : Runnable, Serializable
    private class TU<out T> where T : Runnable, T : Serializable {
        fun t(): T = throw UnsupportedOperationException()
        fun <U : Closeable> u(): U = throw UnsupportedOperationException()
    }

    @Test
    fun `method return type erasure cases`() {
        val m = spectator<TU<RS>>()
        m.t().let { t: Any ->
            assertFalse(t is RS)
            assertTrue(t is Runnable)
            assertFalse(t is Serializable) // Erasure picks the first bound.
        }
        m.u<InputStream>().let { u: Any ->
            assertFalse(u is InputStream)
            assertTrue(u is Closeable)
        }
    }
}
