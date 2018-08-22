package net.corda.djvm.assertions

import net.corda.djvm.costing.RuntimeCostSummary
import org.assertj.core.api.Assertions

@Suppress("MemberVisibilityCanBePrivate")
class AssertiveRuntimeCostSummary(private val costs: RuntimeCostSummary) {

    fun areZero() {
        hasAllocationCost(0)
        hasInvocationCost(0)
        hasJumpCost(0)
        hasThrowCost(0)
    }

    fun hasAllocationCost(cost: Long): AssertiveRuntimeCostSummary {
        Assertions.assertThat(costs.allocationCost.value)
                .`as`("Allocation cost")
                .isEqualTo(cost)
        return this
    }

    fun hasInvocationCost(cost: Long): AssertiveRuntimeCostSummary {
        Assertions.assertThat(costs.invocationCost.value)
                .`as`("Invocation cost")
                .isEqualTo(cost)
        return this
    }

    fun hasInvocationCostGreaterThanOrEqualTo(cost: Long): AssertiveRuntimeCostSummary {
        Assertions.assertThat(costs.invocationCost.value)
                .`as`("Invocation cost")
                .isGreaterThanOrEqualTo(cost)
        return this
    }

    fun hasJumpCost(cost: Long): AssertiveRuntimeCostSummary {
        Assertions.assertThat(costs.jumpCost.value)
                .`as`("Jump cost")
                .isEqualTo(cost)
        return this
    }

    fun hasJumpCostGreaterThanOrEqualTo(cost: Long): AssertiveRuntimeCostSummary {
        Assertions.assertThat(costs.jumpCost.value)
                .`as`("Jump cost")
                .isGreaterThanOrEqualTo(cost)
        return this
    }

    fun hasThrowCost(cost: Long): AssertiveRuntimeCostSummary {
        Assertions.assertThat(costs.throwCost.value)
                .`as`("Throw cost")
                .isEqualTo(cost)
        return this
    }

}
