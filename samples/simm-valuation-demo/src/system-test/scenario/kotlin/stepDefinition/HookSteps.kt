package stepDefinition

import cucumber.api.java.en.Given
import cucumber.api.java.en.Then
import cucumber.api.java.en.When

class HooksSteps {

    @Given("^this is the first step$")
    fun This_Is_The_First_Step() {
        println("\nThis is the first step")
    }

    @When("^this is the second step$")
    fun This_Is_The_Second_Step() {
        println("This is the second step")
    }

    @Then("^this is the third step$")
    fun This_Is_The_Third_Step() {
        println("This is the third step")
    }
}