package net.corda.sandbox.costing

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class RuntimeCostTest {

    @Test
    fun `can increment cost`() {
        val cost = RuntimeCost(10, { "failed" })
        cost.increment(1)
        assertThat(cost.value).isEqualTo(1)
    }

    @Test
    fun `cannot increment cost beyond threshold`() {
        Thread {
            val cost = RuntimeCost(10, { "failed in ${it.name}" })
            assertThatExceptionOfType(ThresholdViolationException::class.java)
                    .isThrownBy { cost.increment(11) }
                    .withMessage("failed in Foo")
            assertThat(cost.value).isEqualTo(11)
        }.apply {
            name = "Foo"
            start()
            join()
        }
    }

}
