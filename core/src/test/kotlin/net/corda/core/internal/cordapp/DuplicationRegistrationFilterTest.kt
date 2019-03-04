package net.corda.core.internal.cordapp

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicationRegistrationFilterTest {

    private val classLoader = ClassLoader.getSystemClassLoader()

    @Test
    fun `ignores anything in the ignore list`() {
        val filter = DuplicateRegistrationFilter(setOf("foo.bar", "foo.baz", "net.corda"))

        assertFalse(filter.shouldNotify("foo.bar.A", classLoader))
        assertFalse(filter.shouldNotify("foo.baz.xyzzy.B", classLoader))
        assertFalse(filter.shouldNotify(ActuallyAContract::class.java.name, classLoader))
    }

    class NotAContract

    class ActuallyAContract : Contract {
        override fun verify(tx: LedgerTransaction) = Unit
    }

    @Test
    fun `ignores anything that isn't a contract`() {
        val filter = DuplicateRegistrationFilter(setOf("foo.bar", "foo.baz"))

        assertFalse(filter.shouldNotify(NotAContract::class.java.name, classLoader))
    }

    @Test
    fun `notifies anything that is a contract`() {
        val filter = DuplicateRegistrationFilter(setOf("foo.bar", "foo.baz"))

        assertTrue(filter.shouldNotify(ActuallyAContract::class.java.name, classLoader))
    }

    @Test
    fun `ignores anything it's seen before`() {
        val filter = DuplicateRegistrationFilter(setOf("foo.bar", "foo.baz"))

        assertTrue(filter.shouldNotify(ActuallyAContract::class.java.name, classLoader))
        assertFalse(filter.shouldNotify(ActuallyAContract::class.java.name, classLoader))
    }
}