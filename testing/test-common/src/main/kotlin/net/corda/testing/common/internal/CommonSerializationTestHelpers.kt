package net.corda.testing.common.internal

import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal._driverSerializationEnv
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("TestSerializationRuleHelper")


fun <T> SerializationEnvironment.asContextEnv(callable: (SerializationEnvironment) -> T): T {
    val property = _driverSerializationEnv
    if (property.get() != null) {
        log.warn("Environment was not cleared up before previous test")
        property.set(null)
    }
    property.set(this)
    try {
        return callable(this)
    } finally {
        property.set(null)
    }
}
