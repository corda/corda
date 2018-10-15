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

        override val mandatory: Boolean get() = true
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

    // TODO sollecitom change
    fun contextualize(currentContext: String?): String? = currentContext

    companion object {

        internal val expectedExceptionTypes: Set<KClass<*>> = setOf(ConfigException.Missing::class, ConfigException.WrongType::class, ConfigException.BadValue::class)

        fun long(key: String): ConfigProperty.Standard<Long> = LongConfigProperty(key)

        fun boolean(key: String): ConfigProperty.Standard<Boolean> = StandardConfigProperty(key, Boolean::class.javaObjectType.simpleName, Config::getBoolean, Config::getBooleanList)

        fun double(key: String): ConfigProperty.Standard<Double> = StandardConfigProperty(key, Double::class.javaObjectType.simpleName, Config::getDouble, Config::getDoubleList)

        fun string(key: String): ConfigProperty.Standard<String> = StandardConfigProperty(key, String::class.java.simpleName, Config::getString, Config::getStringList)

        fun duration(key: String): ConfigProperty.Standard<Duration> = StandardConfigProperty(key, Duration::class.java.simpleName, Config::getDuration, Config::getDurationList)

        // TODO sollecitom change `ConfigObject::class.java.simpleName` to something more human-friendly, like "Configuration" perhaps.
        fun value(key: String, schema: ConfigSchema? = null): ConfigProperty.Standard<ConfigObject> = StandardConfigProperty(key, ConfigObject::class.java.simpleName, Config::getObject, Config::getObjectList, schema)

        fun <ENUM : Enum<ENUM>> enum(key: String, enumClass: KClass<ENUM>): ConfigProperty.Standard<ENUM> = StandardConfigProperty(key, enumClass.java.simpleName, { conf: Config, propertyKey: String -> conf.getEnum(enumClass.java, propertyKey) }, { conf: Config, propertyKey: String -> conf.getEnumList(enumClass.java, propertyKey) })
    }
}

private class LongConfigProperty(key: String) : StandardConfigProperty<Long>(key, Long::class.javaObjectType.simpleName, Config::getLong, Config::getLongList) {

    override fun validate(target: Config): Set<ConfigValidationError> {

        val errors = super.validate(target)
        if (errors.isEmpty() && target.getValue(key).unwrapped().toString().contains(".")) {
            return setOf(ConfigException.WrongType(target.origin(), key, Long::class.javaObjectType.simpleName, Double::class.javaObjectType.simpleName).toValidationError(key, typeName))
        }
        return errors
    }
}

internal open class StandardConfigProperty<TYPE>(override val key: String, typeNameArg: String, private val extractSingleValue: (Config, String) -> TYPE, private val extractListValue: (Config, String) -> List<TYPE>, internal val schema: ConfigSchema? = null) : ConfigProperty.Standard<TYPE> {

    override fun valueIn(configuration: Config) = extractSingleValue.invoke(configuration, key)

    override val typeName: String = schema?.let { "#${it.name ?: "Object@$key"}" } ?: typeNameArg

    override fun optional(defaultValue: TYPE?): ConfigProperty<TYPE?> = OptionalConfigProperty(key, typeName, defaultValue, ::validate, extractSingleValue)

    override fun list(): ConfigProperty.Required<List<TYPE>> = ListConfigProperty(key, typeName, extractListValue, schema)

    override fun toString() = "\"$key\": \"$typeName\""

    override fun validate(target: Config): Set<ConfigValidationError> {

        val errors = mutableSetOf<ConfigValidationError>()
        errors += super.validate(target)
        schema?.let { nestedSchema ->
            val nestedConfig: Config? = target.getConfig(key)
            nestedConfig?.let {
                errors += nestedSchema.validate(nestedConfig).map { error -> error.withContainingPath(key) }
            }
        }
        return errors
    }
}

private class ListConfigProperty<TYPE>(override val key: String, elementTypeName: String, private val extractListValue: (Config, String) -> List<TYPE>, private val elementSchema: ConfigSchema?) : ConfigProperty.Required<List<TYPE>> {

    override val typeName = "List<${elementTypeName.capitalize()}>"

    override fun valueIn(configuration: Config): List<TYPE> = extractListValue.invoke(configuration, key)

    override fun optional(defaultValue: List<TYPE>?): ConfigProperty<List<TYPE>?> = OptionalConfigProperty(key, typeName, defaultValue, ::validate, extractListValue)

    override fun validate(target: Config): Set<ConfigValidationError> {

        val errors = mutableSetOf<ConfigValidationError>()
        errors += super.validate(target)
        elementSchema?.let { schema ->
            errors += valueIn(target).asSequence().map { element -> element as ConfigObject }.map(ConfigObject::toConfig).mapIndexed { index, targetConfig -> schema.validate(targetConfig).map { error -> error.withContainingPath(key, "[$index]") } }.reduce { one, other -> one + other }
        }
        return errors
    }
}

private class OptionalConfigProperty<TYPE>(override val key: String, valueTypeName: String, private val defaultValue: TYPE?, private val validate: (Config) -> Set<ConfigValidationError>, private val extractValue: (Config, String) -> TYPE) : ConfigProperty<TYPE?> {

    override val mandatory: Boolean = false

    override val typeName: String = "$valueTypeName?"

    override fun valueIn(configuration: Config): TYPE? {

        return when {
            isSpecifiedBy(configuration) -> extractValue.invoke(configuration, key)
            else -> defaultValue ?: throw ConfigException.Missing(key)
        }
    }

    override fun validate(target: Config): Set<ConfigValidationError> {

        return when {
            isSpecifiedBy(target) -> validate.invoke(target)
            defaultValue != null -> emptySet()
            else -> setOf(ConfigException.Missing(key).toValidationError(key, typeName))
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

private fun ConfigException.toValidationError(keyName: String, typeName: String): ConfigValidationError {

    return when (this) {
        is ConfigException.Missing -> ConfigValidationError.MissingValue.of(keyName, typeName, message!!)
        is ConfigException.WrongType -> ConfigValidationError.WrongType.of(keyName, typeName, message!!)
        is ConfigException.BadValue -> ConfigValidationError.MissingValue.of(keyName, typeName, message!!)
        else -> throw IllegalStateException("Unsupported ConfigException of type ${this::class.java.name}")
    }
}