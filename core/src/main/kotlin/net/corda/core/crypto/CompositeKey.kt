package net.corda.core.crypto

import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import java.security.PublicKey

// Holds node - weight pairs for a CompositeKey. Ordered first by weight, then by node's hashCode.
@CordaSerializable
data class NodeWeight(val node: PublicKey, val weight: Int): Comparable<NodeWeight> {
    override fun compareTo(other: NodeWeight): Int {
        if(weight == other.weight) {
            return node.hashCode().compareTo(other.node.hashCode())
        }
        else return weight.compareTo(other.weight)
    }
}

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
 * [CompositeKey] maintains a list of [NodeWeigh] which holds child subtree with associated weight carried by child node signatures.
 *
 * The [threshold] specifies the minimum total weight required (in the simple case – the minimum number of child
 * signatures required) to satisfy the sub-tree rooted at this node.
 */
@CordaSerializable
class CompositeKey(val threshold: Int,
                   children: List<NodeWeight>) : PublicKey {
    val children = children.sorted()
    init {
        require (children.size == children.toSet().size) { "Trying to construct CompositeKey with duplicated child nodes." }
        // If we want PublicKey we only keep one key, otherwise it will lead to semantically equivalent trees but having different structures.
        require(children.size > 1) { "Cannot construct CompositeKey with only one child node." }
    }

    companion object {
        // TODO: Get the design standardised and from there define a recognised name
        val ALGORITHM = "X-Corda-CompositeKey"
        // TODO: We should be using a well defined format.
        val FORMAT = "X-Corda-Kryo"
    }

    fun isFulfilledBy(key: PublicKey) = isFulfilledBy(setOf(key))

    override fun getAlgorithm() = ALGORITHM
    override fun getEncoded(): ByteArray = this.serialize().bytes
    override fun getFormat() = FORMAT

    // TODO Can CompositeKey be fulfilled by other composite keys? With composite signature it makes some sense.
    fun isFulfilledBy(keys: Iterable<PublicKey>): Boolean {
        val totalWeight = children.map { it ->
            if (it.node is CompositeKey) {
                if (it.node.isFulfilledBy(keys))
                    it.weight
                else
                    0
            } else {
                if (keys.contains(it.node))
                    it.weight
                else
                    0
            }
        }.sum()
        return totalWeight >= threshold
    }

    val keys: Set<PublicKey>
        get() = children.flatMap { it.node.keys }.toSet() // Uses PublicKey.keys extension.

    // Auto-generated. TODO: remove once data class inheritance is enabled
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompositeKey) return false
        if (threshold != other.threshold) return false
        if (children != other.children) return false

        return true
    }

    override fun hashCode(): Int {
        var result = threshold
        result = 31 * result + children.hashCode()
        return result
    }

    override fun toString() = "(${children.joinToString()})"

    /** A helper class for building a [CompositeKey]. */
    class Builder {
        private val children: MutableList<NodeWeight> = mutableListOf()

        /** Adds a child [CompositeKey] node. Specifying a [weight] for the child is optional and will default to 1. */
        fun addKey(key: PublicKey, weight: Int = 1): Builder {
            children.add(NodeWeight(key, weight))
            return this
        }

        fun addKeys(vararg keys: PublicKey): Builder {
            keys.forEach { addKey(it) }
            return this
        }

        fun addKeys(keys: List<PublicKey>): Builder = addKeys(*keys.toTypedArray())

        /**
         * Builds the [CompositeKey]. If [threshold] is not specified, it will default to
         * the size of the children, effectively generating an "N of N" requirement.
         * During process removes single keys wrapped in [CompositeKey] and enforces ordering on child nodes.
         */
        @Throws(IllegalArgumentException::class)
        fun build(threshold: Int? = null): PublicKey {
            val n = children.size
            if (n > 1)
                return CompositeKey(threshold ?: n, children)
            else if (n == 1) {
                if (threshold != null && threshold != children[0].weight)
                    throw IllegalArgumentException("Trying to build invalid CompositeKey, threshold value different than weight of single child node.")
                return children[0].node // We can assume that this node is a correct CompositeKey.
            }
            else throw IllegalArgumentException("Trying to build CompositeKey without child nodes.")
        }
    }
}

/** Returns the set of all [PublicKey]s contained within the PublicKey. These may be also [CompositeKey]s */
val Iterable<PublicKey>.keys: Set<PublicKey>
    get() = flatMap { it.keys }.toSet()
