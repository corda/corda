package net.corda.contracts.serialization.custom

import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("unused")
class CurrantsySerializer : SerializationCustomSerializer<Currantsy, CurrantsySerializer.Proxy> {
    data class Proxy(val currants: Long)

    override fun fromProxy(proxy: Proxy): Currantsy = Currantsy(proxy.currants)
    override fun toProxy(obj: Currantsy) = Proxy(obj.currants)
}

data class Currantsy(val currants: Long) : Comparable<Currantsy> {
    override fun toString(): String {
        return "$currants juicy currants"
    }

    override fun compareTo(other: Currantsy): Int {
        return currants.compareTo(other.currants)
    }
}
