package net.corda.vega.scenarios.helpers

import cucumber.api.java.After
import cucumber.api.java.Before

class Hooks {

    @Before
    fun beforeScenario() {
        println("\nEnter Before the Scenario")
        println("STAGING_ROOT: ${System.getenv("STAGING_ROOT")}")
        println("Exit Before the Scenario")
    }

    @After
    fun afterScenario() {
        println("\nEnter After the Scenario")
        println("Exit After the Scenario")
    }
}