package net.corda.djvm.analysis

/**
 * Trie data structure to make prefix matching more efficient.
 */
class PrefixTree {

    private class Node(val children: MutableMap<Char, Node> = mutableMapOf())

    private val root = Node()

    /**
     * Add a new prefix to the set.
     */
    fun add(prefix: String) {
        var node = root
        for (char in prefix) {
            val nextNode = node.children.computeIfAbsent(char) { Node() }
            node = nextNode
        }
    }

    /**
     * Check if any of the registered prefixes matches the provided string.
     */
    fun contains(string: String): Boolean {
        var node = root
        for (char in string) {
            val nextNode = node.children[char] ?: return false
            if (nextNode.children.isEmpty()) {
                return true
            }
            node = nextNode
        }
        return false
    }

}