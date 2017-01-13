package net.corda.core.schemas.requery.converters

import io.requery.Converter
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash

/**
 * Converts from a [StateRef] to a Composite Key defined by a [String] txnHash and an [Int] index
 */
class StateRefConverter : Converter<StateRef, Pair<String, Int>> {

    override fun getMappedType(): Class<StateRef> { return StateRef::class.java }

    @Suppress("UNCHECKED_CAST")
    override fun getPersistedType(): Class<Pair<String,Int>> { return Pair::class.java as Class<Pair<String,Int>> }

    override fun getPersistedSize(): Int? { return null }

    override fun convertToPersisted(value: StateRef?): Pair<String,Int>? {
        if (value == null) { return null }
        return Pair(value.txhash.toString(), value.index)
    }

    override fun convertToMapped(type: Class<out StateRef>, value: Pair<String,Int>?): StateRef? {
        if (value == null) { return null }
        return StateRef(SecureHash.parse(value.first), value.second)
    }
}