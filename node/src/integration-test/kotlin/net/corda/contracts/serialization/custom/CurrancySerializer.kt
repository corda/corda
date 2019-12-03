package net.corda.contracts.serialization.custom

import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("unused")
class CurrancySerializer : SerializationCustomSerializer<Currancy, CurrancySerializer.Proxy> {
    data class Proxy(val currants: Long)

    override fun fromProxy(proxy: Proxy): Currancy = Currancy(proxy.currants)
    override fun toProxy(obj: Currancy) = Proxy(obj.currants)
}

data class Currancy(val currants: Long) : Comparable<Currancy> {
    override fun toString(): String {
        return "$currants juicy currants"
    }

    override fun compareTo(other: Currancy): Int {
        return currants.compareTo(other.currants)
    }
}
