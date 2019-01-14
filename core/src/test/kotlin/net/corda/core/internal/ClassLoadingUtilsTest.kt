package net.corda.core.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.lang.IllegalArgumentException

class ClassLoadingUtilsTest {

    interface BaseInterface {}

    interface BaseInterface2 {}

    class ConcreteClassWithEmptyConstructor: BaseInterface {}

    abstract class AbstractClass: BaseInterface

    class ConcreteClassWithNonEmptyConstructor(private val someData: Int): BaseInterface2 {}

    @Test
    fun predicateClassAreLoadedSuccessfully() {
        val classes = createInstancesOfClassesImplementing(BaseInterface::class.java.classLoader, BaseInterface::class.java)

        val classNames = classes.map { it.javaClass.name }

        assertThat(classNames).contains(ConcreteClassWithEmptyConstructor::class.java.name)
        assertThat(classNames).doesNotContain(AbstractClass::class.java.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsExceptionWhenClassDoesNotContainProperConstructors() {
        val classes = createInstancesOfClassesImplementing(BaseInterface::class.java.classLoader, BaseInterface2::class.java)
    }

}