import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(Cucumber::class)
@CucumberOptions(
        glue = arrayOf("net.corda.behave.scenarios"),
        plugin = arrayOf("pretty")
)
@Suppress("KDocMissingDocumentation")
class CucumberTest

fun main(args: Array<out String>) {
    val name: String? = null
    val featureLocation = ""
    val tags = emptyList<String>()
    cucumber.api.cli.Main.main(getArguments(featureLocation, name, tags).toTypedArray())
}

private fun getArguments(featureLocation: String, name: String?, tags: List<String>): List<String> {
    // TODO Tidy up! :D
    return listOf(
            "--glue", "net.corda.behave.scenarios",
            "--plugin", "org.jetbrains.plugins.cucumber.java.run.CucumberJvmSMFormatter",
            featureLocation
    ) + if (name == null) { listOf() } else {
        listOf("--name", name)
    } + if (tags.isNotEmpty()) {
        listOf("--tags", *tags.toTypedArray())
    } else {
        emptyList()
    }
}