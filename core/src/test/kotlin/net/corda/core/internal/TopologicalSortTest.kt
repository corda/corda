package net.corda.core.internal

import net.corda.core.crypto.SecureHash
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class TopologicalSortTest {
    private val topologicalSort = TopologicalSort()
    private val t1 = SecureHash.randomSHA256()
    private val t2 = SecureHash.randomSHA256()
    private val t3 = SecureHash.randomSHA256()
    private val t4 = SecureHash.randomSHA256()

    @Test
    fun issuance() {
        topologicalSort.add(t1, emptySet())
        assertThat(topologicalSort.complete()).containsExactly(t1)
    }

    @Test
    fun `T1 to T2`() {
        topologicalSort.add(t2, setOf(t1))
        topologicalSort.add(t1, emptySet())
        assertThat(topologicalSort.complete()).containsExactly(t1, t2)
    }

    @Test
    fun `T1 to T2, T1 to T3`() {
        topologicalSort.add(t3, setOf(t1))
        topologicalSort.add(t2, setOf(t1))
        topologicalSort.add(t1, emptySet())
        val sorted = topologicalSort.complete()
        assertThat(listOf(t1, t2).map(sorted::indexOf)).isSorted
        assertThat(listOf(t1, t3).map(sorted::indexOf)).isSorted
    }

    @Test
    fun `T1 to T2 to T4, T1 to T3 to T4`() {
        topologicalSort.add(t4, setOf(t2, t3))
        topologicalSort.add(t3, setOf(t1))
        topologicalSort.add(t2, setOf(t1))
        topologicalSort.add(t1, emptySet())
        val sorted = topologicalSort.complete()
        assertThat(listOf(t1, t2, t4).map(sorted::indexOf)).isSorted
        assertThat(listOf(t1, t3, t4).map(sorted::indexOf)).isSorted
    }

    @Test
    fun `T1 to T2 to T3 to T4, T1 to T4`() {
        topologicalSort.add(t4, setOf(t2, t1))
        topologicalSort.add(t3, setOf(t2))
        topologicalSort.add(t2, setOf(t1))
        topologicalSort.add(t1, emptySet())
        val sorted = topologicalSort.complete()
        assertThat(listOf(t1, t2, t3, t4).map(sorted::indexOf)).isSorted
        assertThat(listOf(t1, t4).map(sorted::indexOf)).isSorted
    }
}