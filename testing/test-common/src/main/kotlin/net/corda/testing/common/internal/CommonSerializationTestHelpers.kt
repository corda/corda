package net.corda.testing.common.internal

import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.serialization.internal._driverSerializationEnv
import net.corda.core.serialization.internal._inheritableContextSerializationEnv

fun <T> SerializationEnvironment.asContextEnv(inheritable: Boolean = false, callable: (SerializationEnvironment) -> T): T {
    val property = _driverSerializationEnv
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }
}
