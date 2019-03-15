package net.corda.djvm.assertions

import net.corda.djvm.rewiring.LoadedClass
import org.assertj.core.api.Assertions.*

class AssertiveClassWithByteCode(private val loadedClass: LoadedClass) {

    fun isSandboxed(): AssertiveClassWithByteCode {
        assertThat(loadedClass.type.name).startsWith("sandbox.")
        return this
    }

    fun hasNotBeenModified(): AssertiveClassWithByteCode {
        assertThat(loadedClass.byteCode.isModified)
                .`as`("Byte code has been modified")
                .isEqualTo(false)
        return this
    }

    fun hasBeenModified(): AssertiveClassWithByteCode {
        assertThat(loadedClass.byteCode.isModified)
                .`as`("Byte code has not been modified")
                .isEqualTo(true)
        return this
    }

    fun hasClassLoader(classLoader: ClassLoader): AssertiveClassWithByteCode {
        assertThat(loadedClass.type.classLoader).isEqualTo(classLoader)
        return this
    }

    fun hasClassName(className: String): AssertiveClassWithByteCode {
        assertThat(loadedClass.type.name).isEqualTo(className)
        return this
    }

    fun hasInterface(className: String): AssertiveClassWithByteCode {
        assertThat(loadedClass.type.interfaces.map(Class<*>::getName)).contains(className)
        return this
    }
}
