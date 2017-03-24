package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.security.PublicKey

/**
 * A tree data structure that enables the representation of composite public keys.
 * Notice that with that implementation CompositeKey extends PublicKey. Leaves are represented by single public keys.
 *
 * For complex scenarios, such as *"Both Alice and Bob need to sign to consume a state S"*, we can represent
 * the requirement by creating a tree with a root [CompositeKey], and Alice and Bob as children.
 * The root node would specify *weights* for each of its children and a *threshold* – the minimum total weight required
 * (e.g. the minimum number of child signatures required) to satisfy the tree signature requirement.
 *
 * Using these constructs we can express e.g. 1 of N (OR) or N of N (AND) signature requirements. By nesting we can
 * create multi-level requirements such as *"either the CEO or 3 of 5 of his assistants need to sign"*.
 *
 * [CompositeKey] maintains a list of child nodes – sub-trees, and associated
 * [weights] carried by child node signatures.
 *
 * The [threshold] specifies the minimum total weight required (in the simple case – the minimum number of child
 * signatures required) to satisfy the sub-tree rooted at this node.
 */
@CordaSerializable
class CompositeKey(val threshold: Int,
                   val children: List<PublicKey>, // Can also be CompositeKey subtree.
                   val weights: List<Int>) : PublicKey {
    companion object {
        // TODO: Get the design standardised and from there define a recognised name
        val ALGORITHM = "X-Corda-CompositeKey"
        // TODO: We should be using a well defined format.
        val FORMAT = "X-Corda-Kryo"
    }

    fun isFulfilledBy(key: PublicKey) = isFulfilledBy(setOf(key))

    /** Checks whether any of the given [keys] matches a leaf on the tree */
    fun containsAny(otherKeys: Iterable<PublicKey>) = keys.intersect(otherKeys).isNotEmpty()

    override fun getAlgorithm() = ALGORITHM
    override fun getEncoded(): ByteArray = this.serialize().bytes
    override fun getFormat() = FORMAT

    // TODO Can CompositeKey be fulfilled by other composite keys? With composite signature it makes some sense.
    fun isFulfilledBy(keys: Iterable<PublicKey>): Boolean {
        val totalWeight = children.mapIndexed { i, childNode ->
            if (childNode is CompositeKey) {
                if (childNode.isFulfilledBy(keys))
                    weights[i]
                else
                    0
            } else {
                if (keys.contains(childNode))
                    weights[i]
                else
                    0
            }
        }.sum()
        return totalWeight >= threshold
    }

    val keys: Set<PublicKey>
        get() = children.flatMap { it.keys }.toSet() // Uses PublicKey.keys extension.

    // TODO We need equivalence of CompositeKeys or normalisation when builder is called.
    //  Otherwise we can end up with semantically equivalent trees but different structure. It already can be problematic
    //  when having single PublicKey and PublicKey wrapped in CompositeKey, but that's not the only case.
    // Auto-generated. TODO: remove once data class inheritance is enabled
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as CompositeKey

        if (threshold != other.threshold) return false
        if (weights != other.weights) return false
        if (children != other.children) return false

        return true
    }

    override fun hashCode(): Int {
        var result = threshold
        result = 31 * result + weights.hashCode()
        result = 31 * result + children.hashCode()
        return result
    }

    override fun toString() = "(${children.joinToString()})"

    /** A helper class for building a [CompositeKey]. */
    class Builder() {
        private val children: MutableList<PublicKey> = mutableListOf()
        private val weights: MutableList<Int> = mutableListOf()

        /** Adds a child [CompositeKey] node. Specifying a [weight] for the child is optional and will default to 1. */
        fun addKey(key: PublicKey, weight: Int = 1): Builder {
            children.add(key)
            weights.add(weight)
            return this
        }

        fun addKeys(vararg keys: PublicKey): Builder {
            keys.forEach { addKey(it) }
            return this
        }

        fun addKeys(keys: List<PublicKey>): Builder = addKeys(*keys.toTypedArray())

        /**
         * Builds the [CompositeKey.Node]. If [threshold] is not specified, it will default to
         * the size of the children, effectively generating an "N of N" requirement.
         */
        fun build(threshold: Int? = null): CompositeKey {
            return CompositeKey(threshold ?: children.size, children.toList(), weights.toList())
        }
    }

    /**
     * Returns the enclosed [PublicKey] for a [CompositeKey] with a single leaf node
     *
     * @throws IllegalArgumentException if the [CompositeKey] contains more than one node
     */
    val singleKey: PublicKey
        get() = keys.singleOrNull() ?: throw IllegalStateException("The key is composed of more than one PublicKey primitive")
}

/** Returns the set of all [PublicKey]s contained in the PublicKey that may be also a [CompositeKey] */
val Iterable<PublicKey>.keys: Set<PublicKey>
    get() = flatMap { it.keys }.toSet()
