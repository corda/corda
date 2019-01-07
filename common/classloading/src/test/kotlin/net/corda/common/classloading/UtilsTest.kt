package net.corda.common.classloading

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.lang.IllegalArgumentException
import java.util.function.Consumer
import java.util.function.Predicate

class UtilsTest {

    class ConcretePredicateClassWithEmptyConstructor: Predicate<Int> {
        override fun test(t: Int): Boolean = true
    }

    abstract class AbstractPredicateClass: Predicate<Int>

    class ConcreteConsumerClassWithNonEmptyConstructor(private val someData: Int): Consumer<Int> {
        override fun accept(t: Int) {}
    }

    @Test
    fun predicateClassAreLoadedSuccessfully() {
        val predicates = loadClassesImplementing(this.javaClass.classLoader, Predicate::class.java)

        val predicateClasses = predicates.map { it.javaClass.name }

        assertThat(predicateClasses).contains(ConcretePredicateClassWithEmptyConstructor::class.java.name)
        assertThat(predicateClasses).doesNotContain(AbstractPredicateClass::class.java.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsExceptionWhenClassDoesNotContainProperConstructors() {
        val predicates = loadClassesImplementing(this.javaClass.classLoader, Consumer::class.java)
    }

}