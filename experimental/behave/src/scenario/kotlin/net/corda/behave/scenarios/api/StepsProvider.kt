package net.corda.behave.scenarios.api

interface StepsProvider {
    val name: String
    val stepsDefinition: StepsBlock
}