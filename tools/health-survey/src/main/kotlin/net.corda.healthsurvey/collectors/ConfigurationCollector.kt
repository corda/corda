package net.corda.healthsurvey.collectors

import com.google.gson.GsonBuilder
import com.typesafe.config.*
import net.corda.healthsurvey.output.Report
import java.nio.file.Files
import java.nio.file.Path

class ConfigurationCollector(private val nodeConfigurationFile: Path) : TrackedCollector("Collecting censored node configuration ...") {

    override fun collect(report: Report) {
        if (Files.exists(nodeConfigurationFile)) {
            val config = filterConfig(ConfigFactory.parseReader(Files.newBufferedReader(nodeConfigurationFile)))
            val gson = GsonBuilder().disableHtmlEscaping().create()
            report.addFile("node.conf").withContent(gson.toJson(config))
            complete("Collected censored node configuration")
        } else {
            fail("Failed to find node configuration")
        }
    }

    private fun ConfigObject.withoutKeys(keys: Iterable<String>): ConfigObject {
        var result = this
        for (key in keys) {
            result = result.withoutKey(key)
        }
        return result
    }

    private fun filterConfig(config: ConfigValue, keyMatchPredicate: (String) -> Boolean): Any = when (config) {
        is ConfigObject -> config
                .withoutKeys(config.keys.filter(keyMatchPredicate))
                .map { it.key to filterConfig(it.value, keyMatchPredicate) }
                .associate { it }
        is ConfigList -> config
                .map { filterConfig(it, keyMatchPredicate) }
        else -> config.unwrapped()
    }

    private fun filterConfig(config: Config) = filterConfig(config.root()) {
        it.contains("password", true)
    }

}
