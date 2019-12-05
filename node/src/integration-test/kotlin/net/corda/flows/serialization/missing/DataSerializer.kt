package net.corda.flows.serialization.missing

import net.corda.contracts.serialization.missing.contract.Data
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("unused")
class DataSerializer : SerializationCustomSerializer<Data, DataSerializer.Proxy> {
    data class Proxy(val value: Long)

    override fun fromProxy(proxy: Proxy): Data = Data(proxy.value)
    override fun toProxy(obj: Data) = Proxy(obj.value)
}
