import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
        features = arrayOf("src/scenario/resources/features"),
        glue = arrayOf("net.corda.behave.scenarios"),
        plugin = arrayOf("pretty")
)
@Suppress("KDocMissingDocumentation")
class CucumberTest
