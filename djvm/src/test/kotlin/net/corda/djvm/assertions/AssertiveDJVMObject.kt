package net.corda.djvm.assertions

import org.assertj.core.api.Assertions.*

class AssertiveDJVMObject(private val djvmObj: Any) {

    fun hasClassName(className: String): AssertiveDJVMObject {
        assertThat(djvmObj.javaClass.name).isEqualTo(className)
        return this
    }

    fun isAssignableFrom(clazz: Class<*>): AssertiveDJVMObject {
        assertThat(djvmObj.javaClass.isAssignableFrom(clazz))
        return this
    }

    fun hasGetterValue(methodName: String, value: Any): AssertiveDJVMObject {
        assertThat(djvmObj.javaClass.getMethod(methodName).invoke(djvmObj)).isEqualTo(value)
        return this
    }

    fun hasGetterNullValue(methodName: String): AssertiveDJVMObject {
        assertThat(djvmObj.javaClass.getMethod(methodName).invoke(djvmObj)).isNull()
        return this
    }
}