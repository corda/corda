package net.corda.explorer.model

import net.corda.core.contracts.USD
import net.corda.core.contracts.currency
import org.junit.Test
import kotlin.test.assertFailsWith

class IssuerModelTest {

    @Test
    fun `test issuer regex`() {
        val regex = Regex("corda.issuer.(USD|GBP|CHF)")
        kotlin.test.assertTrue("corda.issuer.USD".matches(regex))
        kotlin.test.assertTrue("corda.issuer.GBP".matches(regex))

        kotlin.test.assertFalse("corda.issuer.USD.GBP".matches(regex))
        kotlin.test.assertFalse("corda.issuer.EUR".matches(regex))
        kotlin.test.assertFalse("corda.issuer".matches(regex))

        kotlin.test.assertEquals(USD, currency("corda.issuer.USD".substringAfterLast(".")))
        assertFailsWith(IllegalArgumentException::class, { currency("corda.issuer.DOLLAR".substringBeforeLast(".")) })
    }
}