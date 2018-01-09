package net.corda.lazyhub

import net.corda.core.internal.uncheckedCast
import org.assertj.core.api.Assertions.catchThrowable
import org.hamcrest.CoreMatchers.*
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.io.Serializable
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals
import kotlin.test.fail

open class LazyHubTests {
    private val lh = lazyHub()

    class Config(val info: String)
    interface A
    interface B {
        val a: A
    }

    class AImpl(val config: Config) : A
    class BImpl(override val a: A) : B
    class Spectator {
        init {
            fail("Should not be instantiated.")
        }
    }

    @Test
    fun `basic functionality`() {
        val config = Config("woo")
        lh.obj(config)
        lh.impl(AImpl::class)
        lh.impl(BImpl::class)
        lh.impl(Spectator::class)
        val b = lh[B::class]
        // An impl is instantiated at most once per LazyHub:
        assertSame(b.a, lh[A::class])
        assertSame(b, lh[B::class])
        // More specific type to expose config without casting:
        val a = lh[AImpl::class]
        assertSame(b.a, a)
        assertSame(config, a.config)
    }

    private fun createA(config: Config): A = AImpl(config) // Declared return type is significant.
    internal open fun createB(): B = fail("Should not be called.")
    @Test
    fun `factory works`() {
        lh.obj(Config("x"))
        lh.factory(this::createA) // Observe private is OK.
        assertSame(AImpl::class.java, lh[A::class].javaClass)
        // The factory declares A not AImpl as its return type, and lh doesn't try to be clever:
        catchThrowable { lh[AImpl::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(AImpl::class.toString(), message)
        }
    }

    @Ignore
    class Subclass : LazyHubTests() { // Should not run as tests.
        @Suppress("unused")
        private fun createA(@Suppress("UNUSED_PARAMETER") config: Config): A = fail("Should not be called.")

        override fun createB() = BImpl(AImpl(Config("Subclass"))) // More specific return type is OK.
    }

    @Suppress("MemberVisibilityCanPrivate")
    internal fun addCreateATo(lh: MutableLazyHub) {
        lh.factory(this::createA)
    }

    @Suppress("MemberVisibilityCanPrivate")
    internal fun addCreateBTo(lh: MutableLazyHub) {
        lh.factory(this::createB)
    }

    @Test
    fun `private factory is not virtual`() {
        val baseMethod = this::createA.javaMethod!!
        // Check the Subclass version would override if baseMethod wasn't private:
        Subclass::class.java.getDeclaredMethod(baseMethod.name, *baseMethod.parameterTypes)
        lh.obj(Config("x"))
        Subclass().addCreateATo(lh)
        lh[A::class] // Should not blow up.
    }

    @Test
    fun `non-private factory is virtual`() {
        Subclass().addCreateBTo(lh)
        assertEquals("Subclass", (lh[B::class].a as AImpl).config.info) // Check overridden function was called.
        // The signature that was added declares B not BImpl as its return type:
        catchThrowable { lh[BImpl::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(BImpl::class.toString(), message)
        }
    }

    private fun returnsYay() = "yay"
    class TakesString(@Suppress("UNUSED_PARAMETER") text: String)

    @Test
    fun `too many providers`() {
        lh.obj("woo")
        lh.factory(this::returnsYay)
        lh.impl(TakesString::class)
        catchThrowable { lh[TakesString::class] }.run {
            assertSame(TooManyProvidersException::class.java, javaClass)
            assertEquals(TakesString::class.constructors.single().parameters[0].toString(), message)
            assertThat(message, containsString(" #0 "))
            assertThat(message, endsWith(TakesString::class.qualifiedName))
        }
    }

    class TakesStringOrInt(val text: String) {
        @Suppress("unused")
        constructor(number: Int) : this(number.toString())
    }

    @Test
    fun `too many providers with alternate constructor`() {
        lh.obj("woo")
        lh.factory(this::returnsYay)
        lh.impl(TakesStringOrInt::class)
        val constructors = TakesStringOrInt::class.constructors.toList()
        catchThrowable { lh[TakesStringOrInt::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(constructors[0].parameters[0].toString(), message)
            assertThat(message, containsString(" #0 "))
            assertThat(message, endsWith(TakesStringOrInt::class.qualifiedName))
            suppressed.single().run {
                assertSame(TooManyProvidersException::class.java, javaClass)
                assertEquals(constructors[1].parameters[0].toString(), message)
                assertThat(message, containsString(" #0 "))
                assertThat(message, endsWith(TakesStringOrInt::class.qualifiedName))
            }
        }
        lh.obj(123)
        assertEquals("123", lh[TakesStringOrInt::class].text)
    }

    @Test
    fun genericClass() {
        class G<out T : Serializable>(val arg: T)
        lh.obj("arg")
        lh.impl(G::class)
        assertEquals("arg", lh[G::class].arg) // Can't inspect type arg T as no such thing exists.
    }

    private fun <X : Closeable, Y : X> ntv(a: Y) = a.toString()
    @Test
    fun `nested type variable`() {
        // First check it's actually legal to pass any old Closeable into the function:
        val arg = Closeable {}
        assertEquals(arg.toString(), ntv(arg))
        // Good, now check LazyHub can do it:
        val ntv: Function1<Closeable, String> = this::ntv
        lh.factory(uncheckedCast<Any, KFunction<String>>(ntv))
        lh.obj(arg)
        assertEquals(arg.toString(), lh[String::class])
    }

    class PTWMB<out Y>(val arg: Y) where Y : Closeable, Y : Serializable
    private class CloseableAndSerializable : Closeable, Serializable {
        override fun close() {}
    }

    @Test
    fun `parameter type with multiple bounds in java`() {
        // At compile time we must pass something Closeable and Serializable into the constructor:
        CloseableAndSerializable().let { assertSame(it, PTWMB(it).arg) }
        // But at runtime only Closeable is needed (and Serializable is not enough) due to the leftmost bound erasure rule:
        lh.impl(PTWMB::class.java)
        lh.obj(object : Serializable {})
        catchThrowable { lh[PTWMB::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertThat(message, containsString(" #0 "))
            assertThat(message, endsWith(PTWMB::class.constructors.single().javaConstructor.toString()))
        }
        val arg = Closeable {}
        lh.obj(arg)
        assertSame(arg, lh[PTWMB::class].arg)
    }

    @Test
    fun `parameter type with multiple bounds in kotlin`() {
        lh.impl(PTWMB::class)
        lh.obj(object : Serializable {})
        catchThrowable { lh[PTWMB::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(PTWMB::class.constructors.single().parameters[0].toString(), message)
            assertThat(message, containsString(" #0 "))
            assertThat(message, containsString(PTWMB::class.qualifiedName))
        }
        val arg = Closeable {}
        lh.obj(arg)
        assertSame(arg, lh[PTWMB::class].arg)
    }

    private fun <Y> ptwmb(arg: Y) where Y : Closeable, Y : Serializable = arg.toString()
    @Test
    fun `factory parameter type with multiple bounds`() {
        val ptwmb: Function1<CloseableAndSerializable, String> = this::ptwmb
        val kFunction = uncheckedCast<Any, KFunction<String>>(ptwmb)
        lh.factory(kFunction)
        lh.obj(object : Serializable {})
        catchThrowable { lh[String::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(kFunction.parameters[0].toString(), message)
            assertThat(message, containsString(" #0 "))
            assertThat(message, endsWith(ptwmb.toString()))
        }
        val arg = Closeable {}
        lh.obj(arg)
        assertEquals(arg.toString(), lh[String::class])
    }

    private fun <Y> upt(a: Y) = a.toString()
    @Test
    fun `unbounded parameter type`() {
        val upt: Function1<Any, String> = this::upt
        val kFunction: KFunction<String> = uncheckedCast(upt)
        lh.factory(kFunction)
        // The only provider for Any is the factory, which is busy:
        catchThrowable { lh[String::class] }.run {
            assertSame(CircularDependencyException::class.java, javaClass)
            assertThat(message, containsString("'$upt'"))
            assertThat(message, endsWith(listOf(upt).toString()))
        }
        lh.obj(Any())
        // This time the factory isn't attempted:
        catchThrowable { lh[String::class] }.run {
            assertSame(TooManyProvidersException::class.java, javaClass)
            assertEquals(kFunction.parameters[0].toString(), message)
            assertThat(message, containsString(" #0 "))
            assertThat(message, endsWith(upt.toString()))
        }
    }

    open class NoPublicConstructor protected constructor()

    @Test
    fun `no public constructor`() {
        catchThrowable { lh.impl(NoPublicConstructor::class) }.run {
            assertSame(NoPublicConstructorsException::class.java, javaClass)
            assertEquals(NoPublicConstructor::class.toString(), message)
        }
        catchThrowable { lh.impl(NoPublicConstructor::class.java) }.run {
            assertSame(NoPublicConstructorsException::class.java, javaClass)
            assertEquals(NoPublicConstructor::class.toString(), message)
        }
    }

    private fun primitiveInt() = 1
    class IntConsumer(@Suppress("UNUSED_PARAMETER") i: Int)
    class IntegerConsumer(@Suppress("UNUSED_PARAMETER") i: Int?)

    @Test
    fun `boxed satisfies primitive`() {
        lh.obj(1)
        lh.impl(IntConsumer::class)
        lh[IntConsumer::class]
    }

    @Test
    fun `primitive satisfies boxed`() {
        lh.factory(this::primitiveInt)
        lh.impl(IntegerConsumer::class.java)
        lh[IntegerConsumer::class]
    }

    // The primary constructor takes two distinct providers:
    class TakesTwoThings(@Suppress("UNUSED_PARAMETER") first: String, @Suppress("UNUSED_PARAMETER") second: Int) {
        // This constructor takes one repeated provider but we count it both times so greediness is 2:
        @Suppress("unused")
        constructor(first: Int, second: Int) : this(first.toString(), second)

        // This constructor would be greediest but is not satisfiable:
        @Suppress("unused")
        constructor(first: Int, second: String, @Suppress("UNUSED_PARAMETER") third: Config) : this(second, first)
    }

    @Test
    fun `equally greedy constructors kotlin`() {
        lh.obj("str")
        lh.obj(123)
        lh.impl(TakesTwoThings::class)
        catchThrowable { lh[TakesTwoThings::class] }.run {
            assertSame(NoUniqueGreediestSatisfiableConstructorException::class.java, javaClass)
            val expected = TakesTwoThings::class.constructors.filter { it.parameters.size == 2 }
            assertEquals(2, expected.size)
            assertThat(message, endsWith(expected.toString()))
        }
    }

    @Test
    fun `equally greedy constructors java`() {
        lh.obj("str")
        lh.obj(123)
        lh.impl(TakesTwoThings::class.java)
        catchThrowable { lh[TakesTwoThings::class] }.run {
            assertSame(NoUniqueGreediestSatisfiableConstructorException::class.java, javaClass)
            val expected = TakesTwoThings::class.java.constructors.filter { it.parameters.size == 2 }
            assertEquals(2, expected.size)
            assertEquals(expected.toString(), message)
        }
    }

    private fun nrt(): String? = fail("Should not be invoked.")
    @Test
    fun `nullable return type is banned`() {
        catchThrowable { lh.factory(this::nrt) }.run {
            assertSame(NullableReturnTypeException::class.java, javaClass)
            assertThat(message, endsWith(this@LazyHubTests::nrt.toString()))
        }
    }

    @Test
    fun unsatisfiableArrayParam() {
        class Impl(@Suppress("UNUSED_PARAMETER") v: Array<String>)
        lh.impl(Impl::class)
        catchThrowable { lh[Impl::class] }.run {
            assertSame(UnsatisfiableArrayException::class.java, javaClass)
            assertEquals(Impl::class.constructors.single().parameters[0].toString(), message)
        }
        // Arrays are only special in real params, you should use getAll to get all the Strings:
        catchThrowable { lh[Array<String>::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(Array<String>::class.java.toString(), message)
        }
        assertEquals(emptyList(), lh.getAll(String::class))
    }

    @Test
    fun arrayParam1() {
        class Impl(val v: Array<String>)
        lh.impl(Impl::class)
        lh.obj("a")
        assertArrayEquals(arrayOf("a"), lh[Impl::class].v)
    }

    @Test
    fun arrayParam2() {
        class Impl(val v: Array<String>)
        lh.impl(Impl::class)
        lh.obj("y")
        lh.obj("x")
        assertArrayEquals(arrayOf("y", "x"), lh[Impl::class].v)
    }

    @Test
    fun nullableArrayParam() {
        class Impl(val v: Array<String>?)
        lh.impl(Impl::class)
        assertEquals(null, lh[Impl::class].v)
    }

    @Test
    fun arraysAreNotCached() {
        class B(val v: Array<String>)
        class A(val v: Array<String>, val b: B)
        class C(val v: Array<String>)
        class D(val v: Array<String>)
        lh.obj("x")
        lh.obj("y")
        lh.impl(A::class)
        lh.impl(B::class)
        val a = lh[A::class]
        a.run {
            assertArrayEquals(arrayOf("x", "y"), v)
            assertArrayEquals(arrayOf("x", "y"), b.v)
            assertNotSame(v, b.v)
        }
        assertSame(lh[B::class].v, a.b.v) // Because it's the same (cached) instance of B.
        lh.impl(C::class)
        lh[C::class].run {
            assertArrayEquals(arrayOf("x", "y"), v)
            assertNotSame(v, a.v)
            assertNotSame(v, a.b.v)
        }
        lh.obj("z")
        lh.impl(D::class)
        lh[D::class].run {
            assertArrayEquals(arrayOf("x", "y", "z"), v)
        }
    }

    class C1(@Suppress("UNUSED_PARAMETER") c2: C2)
    class C2(@Suppress("UNUSED_PARAMETER") c3: String)

    private fun c3(@Suppress("UNUSED_PARAMETER") c2: C2): String {
        fail("Should not be called.")
    }

    @Test
    fun `circularity error kotlin`() {
        lh.impl(C1::class)
        lh.impl(C2::class)
        lh.factory(this::c3)
        catchThrowable { lh[C1::class] }.run {
            assertSame(CircularDependencyException::class.java, javaClass)
            assertThat(message, containsString("'${C2::class}'"))
            assertThat(message, endsWith(listOf(C1::class.constructors.single(), C2::class.constructors.single(), this@LazyHubTests::c3).toString()))
        }
    }

    @Test
    fun `circularity error java`() {
        lh.impl(C1::class.java)
        lh.impl(C2::class.java)
        lh.factory(this::c3)
        catchThrowable { lh[C1::class] }.run {
            assertSame(CircularDependencyException::class.java, javaClass)
            assertThat(message, containsString("'${C2::class}'"))
            assertThat(message, endsWith(listOf(C1::class.constructors.single().javaConstructor, C2::class.constructors.single().javaConstructor, this@LazyHubTests::c3).toString()))
        }
    }

    @Test
    fun `ancestor hub providers are visible`() {
        val c = Config("over here")
        lh.obj(c)
        lh.child().also {
            it.impl(AImpl::class)
            assertSame(c, it[AImpl::class].config)
        }
        lh.child().child().also {
            it.impl(AImpl::class)
            assertSame(c, it[AImpl::class].config)
        }
    }

    @Test
    fun `descendant hub providers are not visible`() {
        val child = lh.child()
        child.obj(Config("over here"))
        lh.impl(AImpl::class)
        catchThrowable { lh[AImpl::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(AImpl::class.constructors.single().parameters.single().toString(), message)
        }
        // Fails even though we go via the child, as the cached AImpl in lh shouldn't have collaborators from descendant hubs:
        catchThrowable { child[AImpl::class] }.run {
            assertSame(NoSuchProviderException::class.java, javaClass)
            assertEquals(AImpl::class.constructors.single().parameters.single().toString(), message)
        }
    }

    class AllConfigs(val configs: Array<Config>)

    @Test
    fun `nearest ancestor with at least one provider wins`() {
        lh.obj(Config("deep"))
        lh.child().also {
            it.child().also {
                it.impl(AllConfigs::class)
                assertEquals(listOf("deep"), it[AllConfigs::class].configs.map { it.info })
            }
            it.obj(Config("shallow1"))
            it.obj(Config("shallow2"))
            it.child().also {
                it.impl(AllConfigs::class)
                assertEquals(listOf("shallow1", "shallow2"), it[AllConfigs::class].configs.map { it.info })
            }
            it.child().also {
                it.obj(Config("local"))
                it.impl(AllConfigs::class)
                assertEquals(listOf("local"), it[AllConfigs::class].configs.map { it.info })
            }
        }
    }

    @Test
    fun `abstract type`() {
        catchThrowable { lh.impl(Runnable::class) }.run {
            assertSame(AbstractTypeException::class.java, javaClass)
            assertEquals(Runnable::class.toString(), message)
        }
        catchThrowable { lh.impl(Runnable::class.java) }.run {
            assertSame(AbstractTypeException::class.java, javaClass)
            assertEquals(Runnable::class.java.toString(), message)
        }
    }

    private interface Service
    open class GoodService : Service
    abstract class BadService1 : Service
    class BadService2 private constructor() : Service

    private fun badService3(): Service? = fail("Should not be called.")
    @Test
    fun `existing providers not removed if new type is bad`() {
        lh.impl(GoodService::class)
        catchThrowable { lh.impl(Service::class, BadService1::class) }.run {
            assertSame(AbstractTypeException::class.java, javaClass)
            assertEquals(BadService1::class.toString(), message)
        }
        catchThrowable { lh.impl(Service::class, BadService2::class) }.run {
            assertSame(NoPublicConstructorsException::class.java, javaClass)
            assertEquals(BadService2::class.toString(), message)
        }
        catchThrowable { lh.impl(Service::class, BadService2::class.java) }.run {
            assertSame(NoPublicConstructorsException::class.java, javaClass)
            assertEquals(BadService2::class.toString(), message)
        }
        // Type system won't let you pass in badService3, but I still want validation up-front:
        catchThrowable { lh.factory(Service::class, uncheckedCast(this::badService3)) }.run {
            assertSame(NullableReturnTypeException::class.java, javaClass)
            assertEquals(this@LazyHubTests::badService3.toString(), message)
        }
        assertSame(GoodService::class.java, lh[Service::class].javaClass)
    }

    class GoodService2 : GoodService()

    @Test
    fun `service providers are removed completely`() {
        lh.impl(GoodService::class)
        assertSame(GoodService::class.java, lh[Service::class].javaClass)
        lh.impl(GoodService::class, GoodService2::class)
        // In particular, GoodService is no longer registered against Service (or Any):
        assertSame(GoodService2::class.java, lh[Service::class].javaClass)
        assertSame(GoodService2::class.java, lh[Any::class].javaClass)
    }

    class JParamExample(@Suppress("UNUSED_PARAMETER") str: String, @Suppress("UNUSED_PARAMETER") num: Int)

    @Test
    fun `JParam has useful toString`() {
        val c = JParamExample::class.java.constructors.single()
        // Parameter doesn't expose its index, here we deliberately pass in the wrong one to see what happens:
        val text = JParam(c.parameters[0], 1, IOException::class.java).toString()
        assertThat(text, containsString(" #1 "))
        assertThat(text, anyOf(containsString(" str "), containsString(" arg0 ")))
        assertThat(text, endsWith(c.toString()))
    }

    private val sideEffects = mutableListOf<Int>()
    private fun sideEffect1() {
        sideEffects.add(1)
    }

    private fun sideEffect2() {
        sideEffects.add(2)
    }

    @Test
    fun `side-effects are idempotent as a consequence of caching of results`() {
        lh.factory(this::sideEffect1)
        assertEquals(listOf(Unit), lh.getAll(Unit::class))
        assertEquals(listOf(1), sideEffects)
        lh.factory(this::sideEffect2)
        assertEquals(listOf(Unit, Unit), lh.getAll(Unit::class)) // Get both results.
        assertEquals(listOf(1, 2), sideEffects) // sideEffect1 didn't run again.
    }

    @Test
    fun `getAll returns empty list when there is nothing to return`() {
        // This is in contrast to the exception thrown by an array param, which would not be useful to replicate here:
        assertEquals(emptyList(), lh.getAll(IOException::class))
    }

    // Two params needed to make primary constructor the winner when both are satisfiable.
    // It's probably true that the secondary will always trigger a CircularDependencyException, but LazyHub isn't clever enough to tell.
    class InvocationSwitcher(@Suppress("UNUSED_PARAMETER") s: String, @Suppress("UNUSED_PARAMETER") t: String) {
        @Suppress("unused")
        constructor(same: InvocationSwitcher) : this(same.toString(), same.toString())
    }

    @Test
    fun `chosen constructor is not set in stone`() {
        lh.impl(InvocationSwitcher::class)
        assertSame(CircularDependencyException::class.java, catchThrowable { lh[InvocationSwitcher::class] }.javaClass)
        lh.obj("alt")
        lh[InvocationSwitcher::class] // Succeeds via other constructor.
    }

    class GreedinessUnits(@Suppress("UNUSED_PARAMETER") v: Array<String>, @Suppress("UNUSED_PARAMETER") z: Int) {
        // Two greediness units even though it's one provider repeated:
        @Suppress("unused")
        constructor(z1: Int, z2: Int) : this(emptyArray(), z1 + z2)
    }

    @Test
    fun `array param counts as one greediness unit`() {
        lh.obj("x")
        lh.obj("y")
        lh.obj(100)
        lh.impl(GreedinessUnits::class)
        assertSame(NoUniqueGreediestSatisfiableConstructorException::class.java, catchThrowable { lh[GreedinessUnits::class] }.javaClass)
    }

    interface TriangleBase
    interface TriangleSide : TriangleBase
    class TriangleImpl : TriangleBase, TriangleSide

    @Test
    fun `provider registered exactly once against each supertype`() {
        lh.impl(TriangleImpl::class)
        lh[TriangleBase::class] // Don't throw TooManyProvidersException.
    }

    interface Service1
    interface Service2
    class ServiceImpl1 : Service1, Service2
    class ServiceImpl2 : Service2

    @Test
    fun `do not leak empty provider list`() {
        lh.impl(ServiceImpl1::class)
        lh.impl(Service2::class, ServiceImpl2::class)
        assertSame(NoSuchProviderException::class.java, catchThrowable { lh[Service1::class] }.javaClass)
    }

    class Global
    class Session(val global: Global, val local: Int)

    @Test
    fun `child can be used to create a scope`() {
        lh.impl(Global::class)
        lh.factory(lh.child().also {
            it.obj(1)
            it.impl(Session::class)
        }, Session::class)
        lh.factory(lh.child().also {
            it.obj(2)
            it.impl(Session::class)
        }, Session::class)
        val sessions = lh.getAll(Session::class)
        val g = lh[Global::class]
        sessions.forEach { assertSame(g, it.global) }
        assertEquals(listOf(1, 2), sessions.map { it.local })
    }
}
