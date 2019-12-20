package net.corda.contracts.fixup.dependent

import net.corda.contracts.fixup.standalone.StandAloneData
import net.corda.core.serialization.SerializationCustomSerializer

/**
 * This custom serializer has DELIBERATELY been added to the dependent CorDapp
 * in order to make it DEPENDENT on the classes inside the standalone CorDapp.
 */
@Suppress("unused")
class StandAloneDataSerializer : SerializationCustomSerializer<StandAloneData, StandAloneDataSerializer.Proxy> {
    data class Proxy(val value: Long)

    override fun fromProxy(proxy: Proxy): StandAloneData = StandAloneData(proxy.value)
    override fun toProxy(obj: StandAloneData) = Proxy(obj.value)
}
