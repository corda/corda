package net.corda.vega.scenarios

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
        glue = arrayOf("net.corda.vega.scenarios"),
        plugin = arrayOf("pretty"),
        features = arrayOf("src/system-test/scenario/resources/features/simm-valuation.feature:5")
)
@Suppress("KDocMissingDocumentation")
class CucumberTest