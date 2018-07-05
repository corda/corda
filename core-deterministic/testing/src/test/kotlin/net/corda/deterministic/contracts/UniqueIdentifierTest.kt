package net.corda.deterministic.contracts

import net.corda.core.contracts.UniqueIdentifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.*
import org.junit.Test
import java.util.*
import kotlin.reflect.full.primaryConstructor
import kotlin.test.assertFailsWith

class UniqueIdentifierTest {
    private companion object {
        private const val NAME = "MyName"
        private val TEST_UUID: UUID = UUID.fromString("00000000-1111-2222-3333-444444444444")
    }

    @Test
    fun testNewInstance() {
        val id = UniqueIdentifier(NAME, TEST_UUID)
        assertEquals("${NAME}_$TEST_UUID", id.toString())
        assertEquals(NAME, id.externalId)
        assertEquals(TEST_UUID, id.id)
    }

    @Test
    fun testPrimaryConstructor() {
        val primary = UniqueIdentifier::class.primaryConstructor ?: throw AssertionError("primary constructor missing")
        assertThat(primary.call(NAME, TEST_UUID)).isEqualTo(UniqueIdentifier(NAME, TEST_UUID))
    }

    @Test
    fun testConstructors() {
        assertEquals(1, UniqueIdentifier::class.constructors.size)
        val ex = assertFailsWith<IllegalArgumentException> { UniqueIdentifier::class.constructors.first().call() }
        assertThat(ex).hasMessage("Callable expects 2 arguments, but 0 were provided.")
    }
}