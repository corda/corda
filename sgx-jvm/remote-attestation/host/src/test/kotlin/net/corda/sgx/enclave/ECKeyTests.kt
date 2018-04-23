package net.corda.sgx.enclave

import org.junit.Assert.assertArrayEquals
import org.junit.Test

@Suppress("KDocMissingDocumentation")
class ECKeyTests {

    @Test
    fun `can get bytes from EC key instantiated from two empty components`() {
        val ecKey = ECKey(byteArrayOf(), byteArrayOf())
        assertArrayEquals(byteArrayOf(), ecKey.bytes)
    }

    @Test
    fun `can get bytes from EC key instantiated from two components`() {
        val ecKey1 = ECKey(byteArrayOf(1), byteArrayOf(2))
        assertArrayEquals(byteArrayOf(1, 2), ecKey1.bytes)

        val ecKey2 = ECKey(byteArrayOf(1, 2, 3, 4), byteArrayOf(5, 6, 7, 8))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), ecKey2.bytes)
    }

}