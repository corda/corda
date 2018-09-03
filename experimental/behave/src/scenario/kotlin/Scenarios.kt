import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
        glue = ["net.corda.behave.scenarios"],
        plugin = ["pretty"]
)

@Suppress("KDocMissingDocumentation")
class CucumberTest
