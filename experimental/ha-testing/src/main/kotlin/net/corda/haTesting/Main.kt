package net.corda.haTesting

import joptsimple.OptionParser
import joptsimple.ValueConverter
import net.corda.core.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable

fun main(args: Array<String>) {

    val logger = LoggerFactory.getLogger("Main")

    val parser = OptionParser()
    MandatoryCommandLineArguments.values().forEach { argSpec -> parser.accepts(argSpec.name).withRequiredArg().withValuesConvertedBy(argSpec.valueConverter).describedAs(argSpec.description) }
    OptionalCommandLineArguments.values().forEach { argSpec -> parser.accepts(argSpec.name).withOptionalArg().withValuesConvertedBy(argSpec.valueConverter).describedAs(argSpec.description) }

    val options = parser.parse(*args)
    try {
        MandatoryCommandLineArguments.values().forEach { require(options.has(it.name)) { "$it is a mandatory option. Please provide it." } }
    } catch (th: Throwable) {
        parser.printHelpOn(System.err)
        throw th
    }
    val scenarioType = ScenarioType.valueOf(options.valueOf(MandatoryCommandLineArguments.scenarioType.name) as String)

    val scenarioRunner: Callable<Boolean> = when(scenarioType) {
        ScenarioType.Cash -> CashScenarioRunner(options)
        ScenarioType.LinearState -> LinearStateScenarioRunner(options)
    }

    try {
        require(scenarioRunner.call()) { "Scenario should pass" }
        System.exit(0)
    } catch (th: Throwable) {
        logger.error("Exception in main()", th)
        System.exit(1)
    }
}

interface CommandLineArguments {
    val name: String
    val valueConverter: ValueConverter<out Any>
    val description: String
}

enum class MandatoryCommandLineArguments(override val valueConverter: ValueConverter<out Any>, override val description: String) : CommandLineArguments {
    haNodeRpcAddress(NetworkHostAndPortValueConverter, "High Available Node RPC address"),
    haNodeRpcUserName(StringValueConverter, "High Available Node RPC user name"),
    haNodeRpcPassword(StringValueConverter, "High Available Node RPC password"),
    normalNodeRpcAddress(NetworkHostAndPortValueConverter, "Normal Node RPC address"),
    normalNodeRpcUserName(StringValueConverter, "Normal Node RPC user name"),
    normalNodeRpcPassword(StringValueConverter, "Normal Node RPC password"),
    scenarioType(StringValueConverter, "Type of scenario to run"),
}

enum class OptionalCommandLineArguments(override val valueConverter: ValueConverter<out Any>, override val description: String) : CommandLineArguments{
    iterationsCount(PositiveIntValueConverter, "Number of iteration to execute"),
}

private enum class ScenarioType {
    Cash,
    LinearState,
}

private object PositiveIntValueConverter : ValueConverter<Int> {
    override fun convert(value: String): Int {
        val result = value.toInt()
        require(result > 0) { "Positive value is expected" }
        return result
    }

    override fun valueType(): Class<out Int> = Int::class.java

    override fun valuePattern(): String = "positive_integer"
}

private object StringValueConverter : ValueConverter<String> {
    override fun convert(value: String) = value

    override fun valueType(): Class<out String> = String::class.java

    override fun valuePattern(): String = "free_form_text"
}

private object NetworkHostAndPortValueConverter : ValueConverter<NetworkHostAndPort> {
    override fun convert(value: String): NetworkHostAndPort = NetworkHostAndPort.parse(value)

    override fun valueType(): Class<out NetworkHostAndPort> = NetworkHostAndPort::class.java

    override fun valuePattern(): String = "host:port"
}