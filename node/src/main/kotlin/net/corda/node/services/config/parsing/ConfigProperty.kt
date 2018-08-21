package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive
import java.time.Duration

// TODO sollecitom introduce a `description` field to specify the nature of the property e.g., "\"address\": NetworkHostAndPort(String)" or "\"port\": Int" or "\"emailAddress\": optional[String]"
interface ConfigProperty<TYPE> {

    val key: String

    @Throws(ConfigException.Missing::class, ConfigException.WrongType::class)
    fun valueIn(configuration: Config): TYPE

    fun <MAPPED> map(function: (TYPE) -> MAPPED): ConfigProperty<MAPPED>

    fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

    fun optional(): ConfigProperty<TYPE?> = OptionalConfigProperty(this)

    companion object {

        fun int(key: String): ConfigProperty<Int> = IntConfigProperty(key)

        fun boolean(key: String): ConfigProperty<Boolean> = BooleanConfigProperty(key)

        fun double(key: String): ConfigProperty<Double> = DoubleConfigProperty(key)

        fun string(key: String): ConfigProperty<String> = StringConfigProperty(key)

        fun duration(key: String): ConfigProperty<Duration> = DurationConfigProperty(key)

        fun value(key: String): ConfigProperty<ConfigObject> = ObjectConfigProperty(key)

        fun valueList(key: String): ConfigProperty<List<ConfigObject>> = ObjectListConfigProperty(key)

        fun <TYPE> functional(key: String, extractValue: (Config, String) -> TYPE): ConfigProperty<TYPE> = FunctionalConfigProperty(key, extractValue)
    }
}

// TODO sollecitom (perhaps) add a proper `ConvertValue` interface, with support for validation and error reporting.
private open class FunctionalConfigProperty<TYPE>(override val key: String, private val extractValue: (Config, String) -> TYPE) : ConfigProperty<TYPE> {

    override fun valueIn(configuration: Config) = extractValue.invoke(configuration, key)

    override fun <MAPPED> map(function: (TYPE) -> MAPPED): ConfigProperty<MAPPED> {

        return FunctionalConfigProperty(key) { config, keyArg -> function.invoke(extractValue.invoke(config, keyArg)) }
    }
}

private class OptionalConfigProperty<TYPE>(private val delegate: ConfigProperty<TYPE>) : ConfigProperty<TYPE?> {

    override val key = delegate.key

    @Throws(ConfigException.WrongType::class)
    override fun valueIn(configuration: Config): TYPE? {

        return if (delegate.isSpecifiedBy(configuration)) {
            delegate.valueIn(configuration)
        } else {
            null
        }
    }

    override fun <MAPPED> map(function: (TYPE?) -> MAPPED): ConfigProperty<MAPPED> {

        return FunctionalConfigProperty(key) { configuration, _ -> function.invoke(valueIn(configuration)) }
    }
}

private class IntConfigProperty(key: String) : FunctionalConfigProperty<Int>(key, Config::getInt)

private class DoubleConfigProperty(key: String) : FunctionalConfigProperty<Double>(key, Config::getDouble)

private class StringConfigProperty(key: String) : FunctionalConfigProperty<String>(key, Config::getString)

private class BooleanConfigProperty(key: String, caseSensitive: Boolean = true) : FunctionalConfigProperty<Boolean>(key, extract(caseSensitive)) {

    private companion object {

        fun extract(caseSensitive: Boolean): (Config, String) -> Boolean {

            return { configuration: Config, key: String ->
                when {
                    caseSensitive -> configuration.getBoolean(key)
                    else -> configuration.getBooleanCaseInsensitive(key)
                }
            }
        }
    }
}

private class DurationConfigProperty(key: String) : FunctionalConfigProperty<Duration>(key, Config::getDuration)

private class ObjectConfigProperty(key: String) : FunctionalConfigProperty<ConfigObject>(key, Config::getObject)

private class ObjectListConfigProperty(key: String) : FunctionalConfigProperty<List<ConfigObject>>(key, Config::getObjectList)