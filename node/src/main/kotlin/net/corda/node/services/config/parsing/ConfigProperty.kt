package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import com.typesafe.config.ConfigObject
import java.time.Duration
import kotlin.reflect.KClass

// TODO sollecitom add a `validValueIn(config: Config): Valid<TYPE>`, with `Valid<TYPE>` including yes/no, error set, `fun <MAPPED> map(transform: (TYPE) -> MAPPED): Valid<MAPPED>` and `fun <MAPPED> flatMap(transform: (TYPE) -> Valid<MAPPED>): Valid<MAPPED>`
interface ConfigProperty<TYPE> : Validator<Config, ConfigValidationError> {

    val key: String
    val typeName: String
    val mandatory: Boolean

    @Throws(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)
    fun valueIn(configuration: Config): TYPE

    @Throws(ConfigException.WrongType::class, ConfigException.BadValue::class)
    fun valueInOrNull(configuration: Config): TYPE? {

        return when {
            isSpecifiedBy(configuration) -> valueIn(configuration)
            else -> null
        }
    }

    interface Required<TYPE> : ConfigProperty<TYPE> {

        fun optional(defaultValue: TYPE? = null): ConfigProperty<TYPE?>
    }

    interface Single<TYPE> : ConfigProperty<TYPE> {

        fun list(): ConfigProperty.Required<List<TYPE>>
    }

    interface Standard<TYPE> : ConfigProperty.Required<TYPE>, ConfigProperty.Single<TYPE>

    fun isSpecifiedBy(configuration: Config): Boolean = configuration.hasPath(key)

    override fun validate(target: Config): Set<ConfigValidationError> {

        try {
            valueIn(target)
            return emptySet()
        } catch (exception: ConfigException) {
            if (expectedExceptionTypes.any { expected -> expected.isInstance(exception) }) {
                return setOf(exception.toValidationError(key, typeName))
            }
            throw exception
        }
    }

    fun contextualize(currentContext: String?): String? = currentContext

    companion object {

        internal val expectedExceptionTypes: Set<KClass<*>> = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)

        fun int(key: String): ConfigProperty.Standard<Int> = StandardConfigProperty(key, Int::class.javaObjectType.simpleName, Config::getInt, Config::getIntList)

        fun boolean(key: String): ConfigProperty.Standard<Boolean> = StandardConfigProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList)

        fun double(key: String): ConfigProperty.Standard<Double> = StandardConfigProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList)

        fun string(key: String): ConfigProperty.Standard<String> = StandardConfigProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList)

        fun duration(key: String): ConfigProperty.Standard<Duration> = StandardConfigProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList)

        // TODO sollecitom change `ConfigObject::class.java.simpleName` to something more human-friendly, like "Configuration" perhaps.
        fun value(key: String): ConfigProperty.Standard<ConfigObject> = StandardConfigProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList)

        fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>): ConfigProperty.Standard<ENUM> = StandardConfigProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) })

//        // TODO sollecitom remove this and provide rawNested without schema and nested with schema, with only difference in validation
//        inline fun <reified TYPE> nested(key: String, schema: ConfigSchema): ConfigProperty.Standard<TYPE> = nested(key, TYPE::class.java, schema)
//
//        fun <TYPE> nested(key: String, type: Class<TYPE>, schema: ConfigSchema): ConfigProperty.Standard<TYPE> = ProxiedNestedConfigProperty(key, type, schema)
    }
}

internal class StandardConfigProperty<TYPE>(override val key: String, override val typeName: String, private val extractSingleValue: (Config, String) -> TYPE, private val extractListValue: (Config, String) -> List<TYPE>, override val mandatory: Boolean = true) : ConfigProperty.Standard<TYPE> {

    override fun valueIn(configuration: Config) = extractSingleValue.invoke(configuration, key)

    override fun optional(defaultValue: TYPE?): ConfigProperty<TYPE?> = OptionalConfigProperty(key, typeName, defaultValue, extractSingleValue)

    override fun list(): ConfigProperty.Required<List<TYPE>> = ListConfigProperty(key, typeName, extractListValue, mandatory)

    override fun toString() = "\"$key\": \"$typeName\""
}

class ListConfigProperty<TYPE>(override val key: String, elementTypeName: String, private val extractListValue: (Config, String) -> List<TYPE>, override val mandatory: Boolean = true) : ConfigProperty.Required<List<TYPE>> {

    override val typeName = "List<${elementTypeName.capitalize()}>"

    override fun valueIn(configuration: Config): List<TYPE> = extractListValue.invoke(configuration, key)

    override fun optional(defaultValue: List<TYPE>?): ConfigProperty<List<TYPE>?> = OptionalConfigProperty(key, typeName, defaultValue, extractListValue)
}

class OptionalConfigProperty<TYPE>(override val key: String, override val typeName: String, private val defaultValue: TYPE?, private val extractValue: (Config, String) -> TYPE) : ConfigProperty<TYPE> {

    override val mandatory: Boolean = false

    override fun valueIn(configuration: Config): TYPE {

        return when {
            isSpecifiedBy(configuration) -> extractValue.invoke(configuration, key)
            else -> defaultValue ?: throw ConfigException.Missing(key)
        }
    }
}

//private class ProxiedNestedConfigProperty<TYPE>(key: String, type: Class<TYPE>, schema: ConfigSchema, mandatory: Boolean = true) : NestedConfigProperty<TYPE>(key, type.simpleName, schema, { configObj -> schema.proxy(configObj.toConfig(), type) }, mandatory)

//internal open class NestedConfigProperty<TYPE>(key: String, typeName: String, val schema: ConfigSchema, extractValue: (ConfigObject) -> TYPE, mandatory: Boolean = true) : FunctionalConfigProperty<TYPE>(key, typeName, { configArg, keyArg -> extractValue.invoke(configArg.getObject(keyArg)) }, mandatory) {
//
//    final override fun validate(target: Config): Set<ConfigValidationError> {
//
//        val standardErrors = super.validate(target)
//        try {
//            return standardErrors + schema.validate(target.getObject(key).toConfig())
//        } catch (exception: ConfigException) {
//            if (ConfigProperty.expectedExceptionTypes.any { expected -> expected.isInstance(exception) }) {
//                return setOf(exception.toValidationError(key, typeName))
//            }
//            throw exception
//        }
//    }
//
//    final override fun contextualize(currentContext: String?) = currentContext?.let { "$it.$key" } ?: key
//}

//private class ProxiedNestedListConfigProperty<TYPE>(key: String, type: Class<TYPE>, schema: ConfigSchema, mandatory: Boolean = true) : NestedListConfigProperty<List<TYPE>>(key, type.simpleName, schema, { configObjList -> configObjList.map { configObj -> schema.proxy(configObj.toConfig(), type) } }, mandatory)

//internal open class NestedListConfigProperty<TYPE>(key: String, typeName: String, val schema: ConfigSchema, extractValue: (List<ConfigObject>) -> TYPE, mandatory: Boolean = true) : FunctionalConfigProperty<TYPE>(key, typeName, { configArg, keyArg -> extractValue.invoke(configArg.getObjectList(keyArg)) }, mandatory) {
//
//    final override fun validate(target: Config): Set<ConfigValidationError> {
//
//        val standardErrors = super.validate(target)
//        try {
//            return standardErrors + schema.validate(target.getObject(key).toConfig())
//        } catch (exception: ConfigException) {
//            if (ConfigProperty.expectedExceptionTypes.any { expected -> expected.isInstance(exception) }) {
//                return setOf(exception.toValidationError(key, typeName))
//            }
//            throw exception
//        }
//    }
//
//    final override fun contextualize(currentContext: String?) = currentContext?.let { "$it.$key" } ?: key
//}

//internal open class FunctionalConfigProperty<TYPE>(override val key: String, typeName: String, private val extractValue: (Config, String) -> TYPE, override val mandatory: Boolean = true) : ConfigProperty.Standard<TYPE> {
//
//    override val typeName = typeName.capitalize()
//
//    override fun valueIn(configuration: Config) = extractValue.invoke(configuration, key)
//
//    override fun <MAPPED> map(mappedTypeName: String?, function: (TYPE) -> MAPPED): ConfigProperty<MAPPED> {
//
//        return FunctionalConfigProperty(key, compositeMappedName(mappedTypeName, typeName), { config, keyArg -> function.invoke(extractValue.invoke(config, keyArg)) }, mandatory)
//    }
//
//    override fun optional(defaultValue: TYPE?): ConfigProperty<TYPE?> {
//        TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun list(): ConfigProperty.Required<List<TYPE>> {
//        TODO("sollecitom not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//
//    override fun toString() = "\"$key\": \"$typeName\""
//}

//private class OptionalConfigProperty<TYPE>(private val delegate: ConfigProperty<TYPE>) : ConfigProperty<TYPE?> {
//
//    override val key = delegate.key
//    override val typeName = "${delegate.typeName}?"
//    override val mandatory = false
//
//    @Throws(ConfigException.WrongType::class)
//    override fun valueIn(configuration: Config): TYPE? {
//
//        return if (delegate.isSpecifiedBy(configuration)) {
//            delegate.valueIn(configuration)
//        } else {
//            null
//        }
//    }
//
//    override fun <MAPPED> map(mappedTypeName: String?, function: (TYPE?) -> MAPPED): ConfigProperty<MAPPED> {
//
//        return FunctionalConfigProperty(key, "${compositeMappedName(mappedTypeName, delegate.typeName)}?", { configuration, _ -> function.invoke(valueIn(configuration)) }, mandatory)
//    }
//
//    override fun toString() = "\"$key\": \"$typeName\""
//}

private fun compositeMappedName(mappedTypeName: String?, originalTypeName: String) = mappedTypeName?.let { "[$originalTypeName => ${it.capitalize()}]" }
        ?: originalTypeName

private fun ConfigException.toValidationError(keyName: String, typeName: String) = ConfigValidationError(keyName, typeName, message!!)