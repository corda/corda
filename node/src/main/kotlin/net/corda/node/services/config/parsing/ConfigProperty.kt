package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import net.corda.nodeapi.internal.config.getBooleanCaseInsensitive
import java.time.Duration

interface ConfigProperty<TYPE> {

    val key: String
    val typeName: String
    val mandatory: Boolean

    @Throws(ConfigException.Missing::class, ConfigException.WrongType::class)
    fun valueIn(configuration: Config): TYPE

    fun <MAPPED> map(mappedTypeName: String? = null, function: (TYPE) -> MAPPED): ConfigProperty<MAPPED>

    fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

    fun optional(): ConfigProperty<TYPE?> = OptionalConfigProperty(this)

    companion object {

        fun int(key: String): ConfigProperty<Int> = IntConfigProperty(key)
        fun intList(key: String): ConfigProperty<List<Int>> = IntListConfigProperty(key)

        fun boolean(key: String): ConfigProperty<Boolean> = BooleanConfigProperty(key)
        fun booleanList(key: String): ConfigProperty<List<Boolean>> = BooleanListConfigProperty(key)

        fun double(key: String): ConfigProperty<Double> = DoubleConfigProperty(key)
        fun doubleList(key: String): ConfigProperty<List<Double>> = DoubleListConfigProperty(key)

        fun string(key: String): ConfigProperty<String> = StringConfigProperty(key)
        fun stringList(key: String): ConfigProperty<List<String>> = StringListConfigProperty(key)

        fun duration(key: String): ConfigProperty<Duration> = DurationConfigProperty(key)
        fun durationList(key: String): ConfigProperty<List<Duration>> = DurationListConfigProperty(key)

        fun value(key: String): ConfigProperty<ConfigObject> = ObjectConfigProperty(key)
        fun valueList(key: String): ConfigProperty<List<ConfigObject>> = ObjectListConfigProperty(key)

        fun <TYPE> functional(key: String, typeName: String, extractValue: (Config, String) -> TYPE): ConfigProperty<TYPE> = FunctionalConfigProperty(key, typeName, extractValue, true)
    }
}

// TODO sollecitom (perhaps) add a proper `ConvertValue` interface, with support for validation and error reporting.
private open class FunctionalConfigProperty<TYPE>(override val key: String, typeName: String, private val extractValue: (Config, String) -> TYPE, override val mandatory: Boolean = true) : ConfigProperty<TYPE> {

    override val typeName = typeName.capitalize()

    override fun valueIn(configuration: Config) = extractValue.invoke(configuration, key)

    override fun <MAPPED> map(mappedTypeName: String?, function: (TYPE) -> MAPPED): ConfigProperty<MAPPED> {

        return FunctionalConfigProperty(key, compositeMappedName(mappedTypeName, typeName), { config, keyArg -> function.invoke(extractValue.invoke(config, keyArg)) }, mandatory)
    }

    override fun toString(): String {

        return "\"$key\": $typeName"
    }
}

private class OptionalConfigProperty<TYPE>(private val delegate: ConfigProperty<TYPE>) : ConfigProperty<TYPE?> {

    override val key = delegate.key
    override val typeName = "${delegate.typeName}?"
    override val mandatory = false

    @Throws(ConfigException.WrongType::class)
    override fun valueIn(configuration: Config): TYPE? {

        return if (delegate.isSpecifiedBy(configuration)) {
            delegate.valueIn(configuration)
        } else {
            null
        }
    }

    override fun <MAPPED> map(mappedTypeName: String?, function: (TYPE?) -> MAPPED): ConfigProperty<MAPPED> {

        return FunctionalConfigProperty(key, "${compositeMappedName(mappedTypeName, delegate.typeName)}?", { configuration, _ -> function.invoke(valueIn(configuration)) }, mandatory)
    }

    override fun toString(): String {

        return "\"$key\": $typeName"
    }
}

private fun compositeMappedName(mappedTypeName: String?, originalTypeName: String) = mappedTypeName?.let { "[$originalTypeName => ${it.capitalize()}]" } ?: originalTypeName

private class IntConfigProperty(key: String) : FunctionalConfigProperty<Int>(key, Int::class.java.simpleName, Config::getInt)
private class IntListConfigProperty(key: String) : FunctionalConfigProperty<List<Int>>(key, "List<${Int::class.java.simpleName}>", Config::getIntList)

private class DoubleConfigProperty(key: String) : FunctionalConfigProperty<Double>(key, Double::class.java.simpleName, Config::getDouble)
private class DoubleListConfigProperty(key: String) : FunctionalConfigProperty<List<Double>>(key, "List<${Double::class.java.simpleName}>", Config::getDoubleList)

private class StringConfigProperty(key: String) : FunctionalConfigProperty<String>(key, String::class.java.simpleName, Config::getString)
private class StringListConfigProperty(key: String) : FunctionalConfigProperty<List<String>>(key, "List<${String::class.java.simpleName}>", Config::getStringList)

private class BooleanConfigProperty(key: String, caseSensitive: Boolean = true) : FunctionalConfigProperty<Boolean>(key, Boolean::class.java.simpleName, extract(caseSensitive)) {

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

private class BooleanListConfigProperty(key: String) : FunctionalConfigProperty<List<Boolean>>(key, "List<${Boolean::class.java.simpleName}>", Config::getBooleanList)

private class DurationConfigProperty(key: String) : FunctionalConfigProperty<Duration>(key, Duration::class.java.simpleName, Config::getDuration)
private class DurationListConfigProperty(key: String) : FunctionalConfigProperty<List<Duration>>(key, "List<${Duration::class.java.simpleName}>", Config::getDurationList)

private class ObjectConfigProperty(key: String) : FunctionalConfigProperty<ConfigObject>(key, "Object", Config::getObject)

private class ObjectListConfigProperty(key: String) : FunctionalConfigProperty<List<ConfigObject>>(key, "List<Object>", Config::getObjectList)