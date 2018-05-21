package net.corda.haTesting

import joptsimple.OptionParser
import joptsimple.ValueConverter
import net.corda.core.utilities.NetworkHostAndPort
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {

    val logger = LoggerFactory.getLogger("Main")

    val parser = OptionParser()
    MandatoryCommandLineArguments.values().forEach { argSpec -> parser.accepts(argSpec.name).withRequiredArg().withValuesConvertedBy(argSpec.valueConverter).describedAs(argSpec.description) }

    val options = parser.parse(*args)
    try {
        MandatoryCommandLineArguments.values().forEach { require(options.has(it.name)) { "$it is a mandatory option. Please provide it." } }
    } catch (th: Throwable) {
        parser.printHelpOn(System.err)
        throw th
    }
    try {
        require(ScenarioRunner(options).call()) { "Scenario should pass" }
        System.exit(0)
    } catch (th: Throwable) {
        logger.error("Exception in main()", th)
        System.exit(1)
    }
}

enum class MandatoryCommandLineArguments(val valueConverter: ValueConverter<out Any>, val description: String) {
    haNodeRpcAddress(NetworkHostAndPortValueConverter, "High Available Node RPC address"),
    haNodeRpcUserName(StringValueConverter, "High Available Node RPC user name"),
    haNodeRpcPassword(StringValueConverter, "High Available Node RPC password"),
    normalNodeRpcAddress(NetworkHostAndPortValueConverter, "Normal Node RPC address"),
    normalNodeRpcUserName(StringValueConverter, "Normal Node RPC user name"),
    normalNodeRpcPassword(StringValueConverter, "Normal Node RPC password"),
}

private object StringValueConverter : ValueConverter<String> {
    override fun convert(value: String) = value

    override fun valueType(): Class<out String> = String::class.java

    override fun valuePattern(): String = "<free_form_text>"
}

private object NetworkHostAndPortValueConverter : ValueConverter<NetworkHostAndPort> {
    override fun convert(value: String): NetworkHostAndPort = NetworkHostAndPort.parse(value)

    override fun valueType(): Class<out NetworkHostAndPort> = NetworkHostAndPort::class.java

    override fun valuePattern(): String = "<host>:<port>"
}