package net.corda.bn.demo.contracts

import com.nhaarman.mockito_kotlin.mock
import org.junit.Test
import kotlin.test.assertEquals

class LoanStateTest {

    private fun mockLoanState(amount: Int) = LoanState(lender = mock(), borrower = mock(), amount = amount, networkId = "network-id")

    @Test(timeout = 300_000)
    fun `loan state helper methods should work`() {
        mockLoanState(10).settle(5).apply { assertEquals(5, amount) }
        mockLoanState(10).settle(5).settle(3).apply { assertEquals(2, amount) }
        mockLoanState(5).settle(0).apply { assertEquals(5, amount) }
        mockLoanState(10).settle(10).apply { assertEquals(0, amount) }
        mockLoanState(10).settle(5).settle(5).apply { assertEquals(0, amount) }
        mockLoanState(5).settle(15).apply { assertEquals(-10, amount) }
    }
}