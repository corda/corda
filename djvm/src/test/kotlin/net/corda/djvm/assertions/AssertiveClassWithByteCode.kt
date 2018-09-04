package net.corda.djvm.assertions

import net.corda.djvm.rewiring.LoadedClass
import org.assertj.core.api.Assertions

class AssertiveClassWithByteCode(private val loadedClass: LoadedClass) {

    fun isSandboxed(): AssertiveClassWithByteCode {
        Assertions.assertThat(loadedClass.type.name).startsWith("sandbox.")
        return this
    }

    fun hasNotBeenModified(): AssertiveClassWithByteCode {
        Assertions.assertThat(loadedClass.byteCode.isModified)
                .`as`("Byte code has been modified")
                .isEqualTo(false)
        return this
    }

    fun hasBeenModified(): AssertiveClassWithByteCode {
        Assertions.assertThat(loadedClass.byteCode.isModified)
                .`as`("Byte code has been modified")
                .isEqualTo(true)
        return this
    }

}
