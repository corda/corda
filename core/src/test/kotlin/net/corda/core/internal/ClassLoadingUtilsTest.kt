package net.corda.core.internal

import com.nhaarman.mockito_kotlin.mock
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.lang.IllegalArgumentException
import java.lang.RuntimeException

class ClassLoadingUtilsTest {

    private val temporaryClassLoader = mock<ClassLoader>()

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

    @Test
    fun `thread context class loader is adjusted, during the function execution`() {
        val result = executeWithThreadContextClassLoader(temporaryClassLoader) {
            assertThat(Thread.currentThread().contextClassLoader).isEqualTo(temporaryClassLoader)
            true
        }

        assertThat(result).isTrue()
        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(temporaryClassLoader)
    }

    @Test
    fun `thread context class loader is set to the initial, even in case of a failure`() {
        assertThatThrownBy { executeWithThreadContextClassLoader(temporaryClassLoader) {
            throw RuntimeException()
        } }.isInstanceOf(RuntimeException::class.java)

        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(temporaryClassLoader)
    }

}