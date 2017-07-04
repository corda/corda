package net.corda.core.crypto

import net.corda.core.serialization.OpaqueBytes
import net.corda.core.serialization.serialize
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompositeKeyTests {
    val aliceKey = generateKeyPair()
    val bobKey = generateKeyPair()
    val charlieKey = generateKeyPair()

    val alicePublicKey = aliceKey.public
    val bobPublicKey = bobKey.public
    val charliePublicKey = charlieKey.public

    val message = OpaqueBytes("Transaction".toByteArray())

    val aliceSignature = aliceKey.sign(message)
    val bobSignature = bobKey.sign(message)
    val charlieSignature = charlieKey.sign(message)

    @Test
    fun `(Alice) fulfilled by Alice signature`() {
        assertTrue { alicePublicKey.isFulfilledBy(aliceSignature.by) }
        assertFalse { alicePublicKey.isFulfilledBy(charlieSignature.by) }
    }

    @Test
    fun `(Alice or Bob) fulfilled by either signature`() {
        val aliceOrBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(threshold = 1)
        assertTrue { aliceOrBob.isFulfilledBy(aliceSignature.by) }
        assertTrue { aliceOrBob.isFulfilledBy(bobSignature.by) }
        assertTrue { aliceOrBob.isFulfilledBy(listOf(aliceSignature.by, bobSignature.by)) }
        assertFalse { aliceOrBob.isFulfilledBy(charlieSignature.by) }
    }

    @Test
    fun `(Alice and Bob) fulfilled by Alice, Bob signatures`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val signatures = listOf(aliceSignature, bobSignature)
        assertTrue { aliceAndBob.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `(Alice and Bob) requires both signatures to fulfil`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        assertFalse { aliceAndBob.isFulfilledBy(listOf(aliceSignature).byKeys()) }
        assertFalse { aliceAndBob.isFulfilledBy(listOf(bobSignature).byKeys()) }
        assertTrue { aliceAndBob.isFulfilledBy(listOf(aliceSignature, bobSignature).byKeys()) }
    }

    @Test
    fun `((Alice and Bob) or Charlie) signature verifies`() {
        // TODO: Look into a DSL for building multi-level composite keys if that becomes a common use case
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val signatures = listOf(aliceSignature, bobSignature)

        assertTrue { aliceAndBobOrCharlie.isFulfilledBy(signatures.byKeys()) }
    }

    @Test
    fun `encoded tree decodes correctly`() {
        val aliceAndBob = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
        val aliceAndBobOrCharlie = CompositeKey.Builder().addKeys(aliceAndBob, charliePublicKey).build(threshold = 1)

        val encoded = aliceAndBobOrCharlie.toBase58String()
        val decoded = parsePublicKeyBase58(encoded)

        assertEquals(decoded, aliceAndBobOrCharlie)
    }

    @Test
    fun `tree canonical form`() {
        assertEquals(CompositeKey.Builder().addKeys(alicePublicKey).build(), alicePublicKey)
        val node1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(1) // threshold = 1
        val node2 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build(2) // threshold = 2
        assertFalse(node2.isFulfilledBy(alicePublicKey))
        // Ordering by weight.
        val tree1 = CompositeKey.Builder().addKey(node1, 13).addKey(node2, 27).build()
        val tree2 = CompositeKey.Builder().addKey(node2, 27).addKey(node1, 13).build()
        assertEquals(tree1, tree2)
        assertEquals(tree1.hashCode(), tree2.hashCode())

        // Ordering by node, weights the same.
        val tree3 = CompositeKey.Builder().addKeys(node1, node2).build()
        val tree4 = CompositeKey.Builder().addKeys(node2, node1).build()
        assertEquals(tree3, tree4)
        assertEquals(tree3.hashCode(), tree4.hashCode())

        // Duplicate node cases.
        val tree5 = CompositeKey.Builder().addKey(node1, 3).addKey(node1, 14).build()
        val tree6 = CompositeKey.Builder().addKey(node1, 14).addKey(node1, 3).build()
        assertEquals(tree5, tree6)

        // Chain of single nodes should throw.
        assertEquals(CompositeKey.Builder().addKeys(tree1).build(), tree1)
    }

    /**
     * Check that verifying a composite signature using the [CompositeSignature] engine works.
     */
    @Test
    fun `composite signature verification`() {
        val twoOfThree = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey, charliePublicKey).build(threshold = 2)
        val engine = CompositeSignature()
        engine.initVerify(twoOfThree)
        engine.update(message.bytes)

        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature)).serialize().bytes) }
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(bobSignature)).serialize().bytes) }
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, bobSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(bobSignature, charlieSignature)).serialize().bytes) }
        assertTrue { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, bobSignature, charlieSignature)).serialize().bytes) }

        // Check the underlying signature is validated
        val brokenBobSignature = DigitalSignature.WithKey(bobSignature.by, aliceSignature.bytes)
        assertFalse { engine.verify(CompositeSignaturesWithKeys(listOf(aliceSignature, brokenBobSignature)).serialize().bytes) }
    }

    @Test()
    fun `composite key constraints`() {
        // Zero weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 0)
        }
        // Negative weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, -1)
        }
        // Zero threshold.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey).build(0)
        }
        // Negative threshold.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey).build(-1)
        }
        // Threshold > Total-weight.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 2).addKey(bobPublicKey, 2).build(5)
        }
        // Threshold value different than weight of single child node.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, 3).build(2)
        }
        // Aggregated weight integer overflow.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKey(alicePublicKey, Int.MAX_VALUE).addKey(bobPublicKey, Int.MAX_VALUE).build()
        }
        // Duplicated children.
        assertFailsWith(IllegalArgumentException::class) {
            CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey, alicePublicKey).build()
        }
        // Duplicated composite key children.
        assertFailsWith(IllegalArgumentException::class) {
            val node1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build()
            val node2 = CompositeKey.Builder().addKeys(bobPublicKey, alicePublicKey).build()
            CompositeKey.Builder().addKeys(node1, node2).build()
        }
    }

    @Test()
    fun `composite key validation with graph cycle detection`() {
        val node1 = CompositeKey.Builder().addKeys(alicePublicKey, bobPublicKey).build() as CompositeKey
        val node2 = CompositeKey.Builder().addKeys(alicePublicKey, node1).build() as CompositeKey
        val node3 = CompositeKey.Builder().addKeys(alicePublicKey, node2).build() as CompositeKey
        val node4 = CompositeKey.Builder().addKeys(alicePublicKey, node3).build() as CompositeKey
        val node5 = CompositeKey.Builder().addKeys(alicePublicKey, node4).build() as CompositeKey
        val node6 = CompositeKey.Builder().addKeys(alicePublicKey, node5, node2).build() as CompositeKey

        // Initially, there is no any graph cycle.
        node1.checkValidity()
        node2.checkValidity()
        node3.checkValidity()
        node4.checkValidity()
        node5.checkValidity()
        // The fact that node6 has a direct reference to node2 and an indirect (via path node5->node4->node3->node2)
        // does not imply a cycle, as expected (independent paths).
        node6.checkValidity()

        // We will create a graph cycle between node5 and node3. Node5 has already a reference to node3 (via node4).
        // To create a cycle, we add a reference (child) from node3 to node5.
        // Children list is immutable, so reflection is used to inject node5 as an extra NodeAndWeight child of node3.
        val field = node3.javaClass.getDeclaredField("children")
        field.isAccessible = true
        val fixedChildren = node3.children.plus(CompositeKey.NodeAndWeight(node5, 1))
        field.set(node3, fixedChildren)

        /* A view of the example graph cycle.
         *
         *               node6
         *              /    \
         *           node5   node2
         *            /
         *         node4
         *         /
         *      node3
         *      /   \
         *   node2  node5
         *    /
         * node1
         *
         */

        // Detect the graph cycle starting from node3.
        assertFailsWith(IllegalArgumentException::class) {
            node3.checkValidity()
        }

        // Detect the graph cycle starting from node4.
        assertFailsWith(IllegalArgumentException::class) {
            node4.checkValidity()
        }

        // Detect the graph cycle starting from node5.
        assertFailsWith(IllegalArgumentException::class) {
            node5.checkValidity()
        }

        // Detect the graph cycle starting from node6.
        // Typically, one needs to test on the root node only (thus, a validity check on node6 would be enough).
        assertFailsWith(IllegalArgumentException::class) {
            node6.checkValidity()
        }

        // Node2 (and all paths below it, i.e. Node1) are outside the graph cycle and thus, there is no impact on them.
        node2.checkValidity()
        node1.checkValidity()
    }
}
