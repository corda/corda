package net.corda.djvm.rules

import foo.bar.sandbox.Callable
import net.corda.djvm.TestBase
import net.corda.djvm.assertions.AssertionExtensions.assertThat
import org.junit.Test
import java.util.*

class ReferenceExtractorTest : TestBase() {

    @Test
    fun `can find method references`() = validate<A> { context ->
        assertThat(context.references)
                .hasClass("java/util/Random")
                .withLocationCount(1)
                .hasMember("java/lang/Object", "<init>", "()V")
                .withLocationCount(1)
                .hasMember("java/util/Random", "<init>", "()V")
                .withLocationCount(1)
                .hasMember("java/util/Random", "nextInt", "()I")
                .withLocationCount(1)
    }

    class A : Callable {
        override fun call() {
            synchronized(this) {
                Random().nextInt()
            }
        }
    }

    @Test
    fun `can find field references`() = validate<B> { context ->
        assertThat(context.references)
                .hasMember(B::class.java.name.replace('.', '/'), "foo", "Ljava/lang/String;")
    }

    class B {
        @JvmField
        val foo: String = ""

        fun test(): String {
            return foo
        }
    }

    @Test
    fun `can find class references`() = validate<C> { context ->
        assertThat(context.references)
                .hasClass(A::class.java.name.replace('.', '/'))
    }

    class C {
        @JvmField
        val foo: A? = null

        fun test(): A? {
            return foo
        }
    }

}
