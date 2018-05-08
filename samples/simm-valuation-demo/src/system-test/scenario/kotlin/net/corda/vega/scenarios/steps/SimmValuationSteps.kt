package net.corda.vega.scenarios.steps

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.api.StepsBlock
import net.corda.vega.scenarios.helpers.SIMMValuation

class SimmValuationSteps : StepsBlock {

    override fun initialize(state: ScenarioState) {
        val simmValuation = SIMMValuation(state)

        Then<String, String>("^node (\\w+) can trade with node (\\w+)$") { nodeA, nodeB ->
            state.withNetwork {
                simmValuation.trade(nodeA, nodeB)
            }
        }

        Then<String>("^node (\\w+) can run portfolio valuation$") { node ->
            state.withNetwork {
                simmValuation.runValuation(node)
            }
        }

        Then<String, Long>("^node (\\w+) portfolio valuation is (\\d+)$") { _, value ->
            state.withNetwork {
                simmValuation.checkValuation(value)
            }
        }
    }
}