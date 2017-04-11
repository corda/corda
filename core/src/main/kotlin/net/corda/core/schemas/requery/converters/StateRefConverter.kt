package net.corda.core.schemas.requery.converters

import io.requery.Converter
import net.corda.core.contracts.StateRef
import net.corda.core.crypto.SecureHash

/**
 * Converts from a [StateRef] to a Composite Key defined by a [String] txnHash and an [Int] index
 */
class StateRefConverter : Converter<StateRef, Pair<String, Int>> {
    override fun getMappedType() = StateRef::class.java

    @Suppress("UNCHECKED_CAST")
    override fun getPersistedType() = Pair::class.java as Class<Pair<String, Int>>

    override fun getPersistedSize() = null

    override fun convertToPersisted(value: StateRef?) = value?.let { Pair(it.txhash.toString(), it.index) }

    override fun convertToMapped(type: Class<out StateRef>, value: Pair<String, Int>?) = value?.let { StateRef(SecureHash.parse(it.first), it.second) }
}