/*
 * Copyright 2015 Distributed Ledger Group LLC.  Distributed as Licensed Company IP to DLG Group Members
 * pursuant to the August 7, 2015 Advisory Services Agreement and subject to the Company IP License terms
 * set forth therein.
 *
 * All other rights reserved.
 */

package core.serialization

import com.esotericsoftware.kryo.Kryo
import org.junit.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KryoTests {
    data class Person(val name: String, val birthday: Instant?)

    private val kryo: Kryo = createKryo()

    @Test
    fun ok() {
        val april_17th = Instant.parse("1984-04-17T00:30:00.00Z")
        val mike = Person("mike", april_17th)
        val bits = mike.serialize(kryo)
        with(bits.deserialize<Person>(kryo)) {
            assertEquals("mike", name)
            assertEquals(april_17th, birthday)
        }
    }

    @Test
    fun nullables() {
        val bob = Person("bob", null)
        val bits = bob.serialize(kryo)
        with(bits.deserialize<Person>(kryo)) {
            assertEquals("bob", name)
            assertNull(birthday)
        }
    }
}