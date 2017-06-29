package net.corda.core.crypto

import net.corda.core.crypto.CompositeKey.NodeAndWeight
import net.corda.core.serialization.CordaSerializable
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
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
 * [CompositeKey] maintains a list of [NodeAndWeight]s which holds child subtree with associated weight carried by child node signatures.
 *
 * The [threshold] specifies the minimum total weight required (in the simple case – the minimum number of child
 * signatures required) to satisfy the sub-tree rooted at this node.
 */
@CordaSerializable
class CompositeKey private constructor (val threshold: Int,
                   children: List<NodeAndWeight>) : PublicKey {
    val children = children.sorted()
    init {
        require(children.size == children.toSet().size) { "Trying to construct CompositeKey with duplicated child nodes." }
        // If we want PublicKey we only keep one key, otherwise it will lead to semantically equivalent trees
        // but having different structures.
        require(children.size > 1) { "Cannot construct CompositeKey with only one child node." }
        // We should ensure threshold is positive, because smaller allowable weight for a node key is 1.
        require(threshold > 0) { "Cannot construct CompositeKey with non-positive threshold." }
        // If threshold is bigger than total weight, then it will never be satisfied.
        require(threshold <= totalWeight()) { "Threshold cannot be bigger than total weight."}
        // TODO: check for cycle detection
    }

    // Method to check if the total weight overflows.
    // Unlike similar solutions that use long conversion, this approach takes advantage of the minimum weight being 1.
    private fun totalWeight(): Int {
        var sum = 0
        for (nodeAndWeight in children) {
            sum += nodeAndWeight.weight // Minimum weight is 1.
            require(sum < 1) { "Integer overflow detected. Total weight surpasses the maximum accepted value." }
        }
        return sum
    }

    /**
     * Holds node - weight pairs for a CompositeKey. Ordered first by weight, then by node's hashCode.
     * Each node should be assigned with a positive weight to avoid certain types of weight underflow attacks.
     */
    @CordaSerializable
    data class NodeAndWeight(val node: PublicKey, val weight: Int): Comparable<NodeAndWeight>, ASN1Object() {

        init {
            // We don't allow zero or negative weights. Minimum weight = 1.
            require (weight > 0) { "Trying to construct CompositeKey Node with non-positive weight." }
        }
        override fun compareTo(other: NodeAndWeight): Int {
            if (weight == other.weight) {
                return node.hashCode().compareTo(other.node.hashCode())
            }
            else return weight.compareTo(other.weight)
        }

        override fun toASN1Primitive(): ASN1Primitive {
            val vector = ASN1EncodableVector()
            vector.add(DERBitString(node.encoded))
            vector.add(ASN1Integer(weight.toLong()))
            return DERSequence(vector)
        }
    }

    companion object {
        val ALGORITHM = CompositeSignature.ALGORITHM_IDENTIFIER.algorithm.toString()
    }

    /**
     * Takes single PublicKey and checks if CompositeKey requirements hold for that key.
     */
    fun isFulfilledBy(key: PublicKey) = isFulfilledBy(setOf(key))

    override fun getAlgorithm() = ALGORITHM
    override fun getEncoded(): ByteArray {
        val keyVector = ASN1EncodableVector()
        val childrenVector = ASN1EncodableVector()
        children.forEach {
            childrenVector.add(it.toASN1Primitive())
        }
        keyVector.add(ASN1Integer(threshold.toLong()))
        keyVector.add(DERSequence(childrenVector))
        return SubjectPublicKeyInfo(CompositeSignature.ALGORITHM_IDENTIFIER, DERSequence(keyVector)).encoded
    }
    override fun getFormat() = ASN1Encoding.DER

    /**
     * Function checks if the public keys corresponding to the signatures are matched against the leaves of the composite
     * key tree in question, and the total combined weight of all children is calculated for every intermediary node.
     * If all thresholds are satisfied, the composite key requirement is considered to be met.
     */
    fun isFulfilledBy(keysToCheck: Iterable<PublicKey>): Boolean {
        if (keysToCheck.any { it is CompositeKey } ) return false
        val totalWeight = children.map { (node, weight) ->
            if (node is CompositeKey) {
                if (node.isFulfilledBy(keysToCheck)) weight else 0
            } else {
                if (keysToCheck.contains(node)) weight else 0
            }
        }.sum()
        return totalWeight >= threshold
    }

    /**
     * Set of all leaf keys of that CompositeKey.
     */
    val leafKeys: Set<PublicKey>
        get() = children.flatMap { it.node.keys }.toSet() // Uses PublicKey.keys extension.

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
        private val children: MutableList<NodeAndWeight> = mutableListOf()

        /** Adds a child [CompositeKey] node. Specifying a [weight] for the child is optional and will default to 1. */
        fun addKey(key: PublicKey, weight: Int = 1): Builder {
            children.add(NodeAndWeight(key, weight))
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
                require(threshold == null || threshold == children.first().weight)
                    { "Trying to build invalid CompositeKey, threshold value different than weight of single child node." }
                return children.first().node // We can assume that this node is a correct CompositeKey.
            }
            else throw IllegalArgumentException("Trying to build CompositeKey without child nodes.")
        }
    }
}

/**
 * Expands all [CompositeKey]s present in PublicKey iterable to set of single [PublicKey]s.
 * If an element of the set is a single PublicKey it gives just that key, if it is a [CompositeKey] it returns all leaf
 * keys for that composite element.
 */
val Iterable<PublicKey>.expandedCompositeKeys: Set<PublicKey>
    get() = flatMap { it.keys }.toSet()