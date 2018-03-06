package net.corda.behave.scenarios.steps.cordapp

import net.corda.behave.scenarios.StepsBlock

fun simmValuationSteps(steps: StepsBlock) = steps {

    Then<String,String>("^node (\\w+) can trade with node (\\w+)$") { nodeA, nodeB ->
        simmValuation.trade(nodeA, nodeB)
    }

    Then<String>("^node (\\w+) can run portfolio valuation$") { node ->
        simmValuation.runValuation(node)
    }

    Then<String,Long>("^node (\\w+) portfolio valuation is (\\d+)$") { node, value ->
        simmValuation.checkValuation(value)
    }
}
