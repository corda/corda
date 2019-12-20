package net.corda.contracts.fixup.dependent

import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("unused")
class DependentDataSerializer : SerializationCustomSerializer<DependentData, DependentDataSerializer.Proxy> {
    data class Proxy(val value: Long)

    override fun fromProxy(proxy: Proxy): DependentData = DependentData(proxy.value)
    override fun toProxy(obj: DependentData) = Proxy(obj.value)
}
