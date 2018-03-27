package net.corda.vega.scenarios

import net.corda.behave.scenarios.StepsContainer
import net.corda.behave.scenarios.StepsProvider
import net.corda.vega.scenarios.helpers.SIMMValuation

//class SIMMValuationStepsProvider : StepsProvider {
//
//    override val name: String
//        get() = SIMMValuationStepsProvider::javaClass.name
//
//    override val stepsDefinition: (StepsBlock) -> Unit
//        get() = ::simmValuationSteps
//}
//
//fun simmValuationSteps(steps: StepsBlock) = steps {
//
//    val simmValuation = SIMMValuation(state)
//
//    Then<String,String>("^node (\\w+) can trade with node (\\w+)$") { nodeA, nodeB ->
//        simmValuation.trade(nodeA, nodeB)
//    }
//
//    Then<String>("^node (\\w+) can run portfolio valuation$") { node ->
//        simmValuation.runValuation(node)
//    }
//
//    Then<String,Long>("^node (\\w+) portfolio valuation is (\\d+)$") { _, value ->
//        simmValuation.checkValuation(value)
//    }
//}