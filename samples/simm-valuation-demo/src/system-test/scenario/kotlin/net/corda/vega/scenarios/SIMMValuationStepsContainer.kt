package net.corda.vega.scenarios

import net.corda.behave.scenarios.ScenarioState
import net.corda.behave.scenarios.StepsContainer

class SIMMValuationStepsContainer(state: ScenarioState) : StepsContainer(state) {

    private val stepDefinitions: List<(StepsBlock) -> Unit> = listOf(
            ::simmValuationSteps
    )

    init {
        println("SIMMValuationStepsContainer init ...")
        stepDefinitions.forEach { it({ steps(it) }) }
    }
}
