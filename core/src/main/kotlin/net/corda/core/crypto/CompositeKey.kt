/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.core.crypto

import net.corda.core.DeleteForDJVM
import net.corda.core.KeepForDJVM
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.exactAdd
import net.corda.core.utilities.sequence
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import java.security.PublicKey
import java.util.*

/**
 * A tree data structure that enables the representation of composite public keys, which are used to represent
 * the signing requirements for multi-signature scenarios such as RAFT notary services. A composite key is a list
 * of leaf keys and their contributing weight, and each leaf can be a conventional single key or a composite key.
 * Keys contribute their weight to the total if they are matched by the signature.
 *
 * For complex scenarios, such as *"Both Alice and Bob need to sign to consume a state S"*, we can represent
 * the requirement by creating a tree with a root [CompositeKey], and Alice and Bob as children.
 * The root node would specify *weights* for each of its children and a *threshold* – the minimum total weight required
 * (e.g. the minimum number of child signatures required) to satisfy the tree signature requirement.
 *
 * Using these constructs we can express e.g. 1 of N (OR) or N of N (AND) signature requirements. By nesting we can
 * create multi-level requirements such as *"either the CEO or 3 of 5 of his assistants need to sign"*.
 *
 * @property threshold specifies the minimum total weight required (in the simple case – the minimum number of child
 * signatures required) to satisfy the sub-tree rooted at this node.
 */
@KeepForDJVM
@CordaSerializable
class CompositeKey private constructor(val threshold: Int, children: List<NodeAndWeight>) : PublicKey {
    companion object {
        const val KEY_ALGORITHM = "COMPOSITE"
        /**
         * Build a composite key from a DER encoded form.
         */
        fun getInstance(encoded: ByteArray) = getInstance(ASN1Primitive.fromByteArray(encoded))

        fun getInstance(asn1: ASN1Primitive): PublicKey {
            val keyInfo = SubjectPublicKeyInfo.getInstance(asn1)
            require(keyInfo.algorithm.algorithm == CordaObjectIdentifier.COMPOSITE_KEY)
            val sequence = ASN1Sequence.getInstance(keyInfo.parsePublicKey())
            val threshold = ASN1Integer.getInstance(sequence.getObjectAt(0)).positiveValue.toInt()
            val sequenceOfChildren = ASN1Sequence.getInstance(sequence.getObjectAt(1))
            val builder = Builder()
            val listOfChildren = sequenceOfChildren.objects.toList()
            listOfChildren.forEach { childAsn1 ->
                require(childAsn1 is ASN1Sequence)
                val childSeq = childAsn1 as ASN1Sequence
                val key = Crypto.decodePublicKey((childSeq.getObjectAt(0) as DERBitString).bytes)
                val weight = ASN1Integer.getInstance(childSeq.getObjectAt(1))
                builder.addKey(key, weight.positiveValue.toInt())
            }
            return builder.build(threshold)
        }
        // Required for sorting [children] list. To ensure a deterministic way of adding children required for equality
        // checking, [children] list is sorted during construction. A DESC ordering in the [NodeAndWeight.weight] field
        // will improve efficiency, because keys with bigger "weights" are the first to be checked and thus the
        // threshold requirement might be met earlier without requiring a full [children] scan.
        // TODO: node.encoded.sequence() might be expensive, consider a faster deterministic compareTo implementation
        //      for public keys in general.
        private val descWeightComparator = compareBy<NodeAndWeight>({ -it.weight }, { it.node.encoded.sequence() })
    }

    /**
     * Τhe order of the children may not be the same to what was provided in the builder.
     */
    val children: List<NodeAndWeight> = children.sortedWith(descWeightComparator)

    init {
        // TODO: replace with the more extensive, but slower, checkValidity() test.
        checkConstraints()
    }

    @Transient
    private var validated = false

    // Check for key duplication, threshold and weight constraints and test for aggregated weight integer overflow.
    private fun checkConstraints() {
        require(children.size == children.toSet().size) { "CompositeKey with duplicated child nodes detected." }
        // If we want PublicKey we only keep one key, otherwise it will lead to semantically equivalent trees
        // but having different structures.
        require(children.size > 1) { "CompositeKey must consist of two or more child nodes." }
        // We should ensure threshold is positive, because smaller allowable weight for a node key is 1.
        require(threshold > 0) { "CompositeKey threshold is set to $threshold, but it should be a positive integer." }
        // If threshold is bigger than total weight, then it will never be satisfied.
        val totalWeight = totalWeight()
        require(threshold <= totalWeight) {
            "CompositeKey threshold: $threshold cannot be bigger than aggregated weight of child nodes: $totalWeight"
        }
    }

    // Graph cycle detection in the composite key structure to avoid infinite loops on CompositeKey graph traversal and
    // when recursion is used (i.e. in isFulfilledBy()).
    // An IdentityHashMap Vs HashMap is used, because a graph cycle causes infinite loop on the CompositeKey.hashCode().
    private fun cycleDetection(visitedMap: IdentityHashMap<CompositeKey, Boolean>) {
        for ((node) in children) {
            if (node is CompositeKey) {
                val curVisitedMap = IdentityHashMap<CompositeKey, Boolean>()
                curVisitedMap.putAll(visitedMap)
                // We can't print the node details, because doing so involves serializing the node, which we can't
                // do because of the cyclic graph.
                require(!curVisitedMap.contains(node)) { "Cycle detected for CompositeKey" }
                curVisitedMap[node] = true
                node.cycleDetection(curVisitedMap)
            }
        }
    }

    /**
     * This method will detect graph cycles in the full composite key structure to protect against infinite loops when
     * traversing the graph and key duplicates in the each layer. It also checks if the threshold and weight constraint
     * requirements are met, while it tests for aggregated-weight integer overflow.
     * In practice, this method should be always invoked on the root [CompositeKey], as it inherently
     * validates the child nodes (all the way till the leaves).
     */
    fun checkValidity() {
        if (validated) return
        val visitedMap = IdentityHashMap<CompositeKey, Boolean>()
        visitedMap[this] = true
        cycleDetection(visitedMap) // Graph cycle testing on the root node.
        checkConstraints()
        for ((node, _) in children) {
            if (node is CompositeKey) {
                // We don't need to check for cycles on the rest of the nodes (testing on the root node is enough).
                node.checkConstraints()
            }
        }
        validated = true
    }

    // Method to check if the total (aggregated) weight of child nodes overflows.
    // Unlike similar solutions that use long conversion, this approach takes advantage of the minimum weight being 1.
    private fun totalWeight(): Int {
        var sum = 0
        for ((_, weight) in children) {
            require(weight > 0) { "Non-positive weight: $weight detected." }
            sum = sum exactAdd weight // Add and check for integer overflow.
        }
        return sum
    }

    /**
     * Holds node - weight pairs for a CompositeKey. Ordered first by weight, then by node's hashCode.
     * Each node should be assigned with a positive weight to avoid certain types of weight underflow attacks.
     */
    @KeepForDJVM
    @CordaSerializable
    data class NodeAndWeight(val node: PublicKey, val weight: Int) : Comparable<NodeAndWeight>, ASN1Object() {
        init {
            // We don't allow zero or negative weights. Minimum weight = 1.
            require(weight > 0) { "A non-positive weight was detected. Node info: $this" }
        }

        override fun compareTo(other: NodeAndWeight): Int {
            return if (weight == other.weight)
                // TODO: this might be expensive, consider a faster deterministic compareTo implementation when weights are equal.
                node.encoded.sequence().compareTo(other.node.encoded.sequence())
            else
                weight.compareTo(other.weight)
        }

        override fun toASN1Primitive(): ASN1Primitive {
            val vector = ASN1EncodableVector()
            vector.add(DERBitString(node.encoded))
            vector.add(ASN1Integer(weight.toLong()))
            return DERSequence(vector)
        }

        override fun toString(): String {
            return "Public key: ${node.toStringShort()}, weight: $weight"
        }
    }

    /**
     * Takes single [PublicKey] and checks if [CompositeKey] requirements hold for that key.
     */
    fun isFulfilledBy(key: PublicKey) = isFulfilledBy(setOf(key))

    override fun getAlgorithm() = KEY_ALGORITHM

    override fun getEncoded(): ByteArray {
        val keyVector = ASN1EncodableVector()
        val childrenVector = ASN1EncodableVector()
        children.forEach {
            childrenVector.add(it.toASN1Primitive())
        }
        keyVector.add(ASN1Integer(threshold.toLong()))
        keyVector.add(DERSequence(childrenVector))
        return SubjectPublicKeyInfo(AlgorithmIdentifier(CordaObjectIdentifier.COMPOSITE_KEY), DERSequence(keyVector)).encoded
    }

    override fun getFormat() = ASN1Encoding.DER

    // Return true when and if the threshold requirement is met.
    private fun checkFulfilledBy(keysToCheck: Iterable<PublicKey>): Boolean {
        var totalWeight = 0
        children.forEach { (node, weight) ->
            if (node is CompositeKey) {
                if (node.checkFulfilledBy(keysToCheck)) totalWeight += weight
            } else {
                if (node in keysToCheck) totalWeight += weight
            }
            if (totalWeight >= threshold) return true
        }
        return false
    }

    /**
     * Function checks if the public keys corresponding to the signatures are matched against the leaves of the composite
     * key tree in question, and the total combined weight of all children is calculated for every intermediary node.
     * If all thresholds are satisfied, the composite key requirement is considered to be met.
     */
    fun isFulfilledBy(keysToCheck: Iterable<PublicKey>): Boolean {
        // We validate keys only when checking if they're matched, as this checks subkeys as a result.
        // Doing these checks at deserialization/construction time would result in duplicate checks.
        checkValidity()
        if (keysToCheck.any { it is CompositeKey }) return false
        return checkFulfilledBy(keysToCheck)
    }

    /**
     * Set of all leaf keys of that [CompositeKey].
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
    @DeleteForDJVM
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
         * the total (aggregated) weight of the children, effectively generating an "N of N" requirement.
         * During process removes single keys wrapped in [CompositeKey] and enforces ordering on child nodes.
         *
         * @param threshold specifies the minimum total weight required (in the simple case – the minimum number of child
         * signatures required) to satisfy the sub-tree rooted at this node.
         * @throws IllegalArgumentException if the threshold value is invalid.
         * @throws IllegalStateException if the composite key that would be generated from the current state of the builder
         * is invalid (for example it would contain no keys).
         */
        fun build(threshold: Int? = null): PublicKey {
            require(threshold == null || threshold > 0)
            val n = children.size
            return when {
                n > 1 -> CompositeKey(threshold ?: children.map { (_, weight) -> weight }.sum(), children)
                n == 1 -> {
                    require(threshold == null || threshold == children.first().weight)
                    { "Trying to build invalid CompositeKey, threshold value different than weight of single child node." }
                    // Returning the only child node which is [PublicKey] itself. We need to avoid single-key [CompositeKey] instances,
                    // as there are scenarios where developers expected the underlying key and its composite versions to be equivalent.
                    children.first().node
                }
                else -> throw IllegalStateException("Trying to build CompositeKey without child nodes.")
            }
        }
    }
}
