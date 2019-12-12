package net.corda.flows.serialization.missing

import net.corda.contracts.serialization.missing.contract.CustomData
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("unused")
class CustomDataSerializer : SerializationCustomSerializer<CustomData, CustomDataSerializer.Proxy> {
    data class Proxy(val value: Long)

    override fun fromProxy(proxy: Proxy): CustomData = CustomData(proxy.value)
    override fun toProxy(obj: CustomData) = Proxy(obj.value)
}
