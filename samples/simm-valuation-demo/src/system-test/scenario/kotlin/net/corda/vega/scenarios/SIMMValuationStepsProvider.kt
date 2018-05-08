package net.corda.vega.scenarios

import net.corda.behave.scenarios.api.StepsBlock
import net.corda.behave.scenarios.api.StepsProvider
import net.corda.vega.scenarios.steps.SimmValuationSteps

class SIMMValuationStepsProvider : StepsProvider {

    override val name: String
        get() = SIMMValuationStepsProvider::javaClass.name

    override val stepsDefinition: StepsBlock
        get() = SimmValuationSteps()
}
