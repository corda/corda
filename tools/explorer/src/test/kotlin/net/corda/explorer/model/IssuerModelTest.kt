package net.corda.explorer.model

import net.corda.finance.USD
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerModelTest {
    @Test
    fun `test issuer regex`() {
        val regex = Regex("corda.issuer.(USD|GBP|CHF)")
        assertTrue("corda.issuer.USD".matches(regex))
        assertTrue("corda.issuer.GBP".matches(regex))

        assertFalse("corda.issuer.USD.GBP".matches(regex))
        assertFalse("corda.issuer.EUR".matches(regex))
        assertFalse("corda.issuer".matches(regex))

        assertEquals(USD, Currency.getInstance("corda.issuer.USD".substringAfterLast(".")))
        assertFailsWith(IllegalArgumentException::class) {
            Currency.getInstance("corda.issuer.DOLLAR".substringBeforeLast("."))
        }
    }
}