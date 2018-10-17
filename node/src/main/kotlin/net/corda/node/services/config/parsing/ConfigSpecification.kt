package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import java.time.Duration
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class ConfigSpecification(name: String?) : ConfigSchema {

    private val mutableProperties = mutableSetOf<ConfigProperty<*>>()

    override val properties: Set<ConfigProperty<*>> = mutableProperties

    private val schema: ConfigSchema by lazy {

        ConfigPropertySchema(name, properties)
    }

    fun long(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Long> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, ConfigProperty.Companion::long)

    fun boolean(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Boolean> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, ConfigProperty.Companion::boolean)

    fun double(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Double> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, ConfigProperty.Companion::double)

    fun string(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<String> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, ConfigProperty.Companion::string)

    fun duration(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Duration> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, ConfigProperty.Companion::duration)

    fun nestedObject(schema: ConfigSchema? = null, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ConfigObject> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, { k, s -> ConfigProperty.nestedObject(k, schema, s) })

    fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, { k, s -> ConfigProperty.enum(k, enumClass, s) })

    override val name: String? get() = schema.name

    override fun description() = schema.description()

    override fun validate(target: Config, options: ConfigProperty.ValidationOptions?) = schema.validate(target, options)

    override fun describe(configuration: Config) = schema.describe(configuration)
}

inline fun <reified ENUM : Enum<ENUM>> ConfigSpecification.enum(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key, ENUM::class, sensitive)

interface PropertyDelegate<TYPE> {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty<TYPE>>

    interface Required<TYPE> {

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Required<TYPE>>

        fun optional(defaultValue: TYPE? = null): PropertyDelegate<TYPE?>
    }

    interface Single<TYPE> {

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Single<TYPE>>

        fun list(): PropertyDelegate.Required<List<TYPE>>
    }

    interface Standard<TYPE> : PropertyDelegate.Required<TYPE>, PropertyDelegate.Single<TYPE> {

        override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Standard<TYPE>>

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, ConfigValidationError>): PropertyDelegate.Standard<MAPPED>
    }
}

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.map(noinline convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, ConfigValidationError>): PropertyDelegate.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)

private class PropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (ConfigProperty<*>) -> Unit, private val construct: (String, Boolean) -> ConfigProperty.Standard<TYPE>) : PropertyDelegate.Standard<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Standard<TYPE>> {

        val prop = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, ConfigProperty.Standard<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): ConfigProperty.Standard<TYPE> = prop
        }
    }

    override fun list(): PropertyDelegate.Required<List<TYPE>> = ListPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).list() })

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })

    override fun <MAPPED : Any> map(mappedTypeName: String, convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, ConfigValidationError>): PropertyDelegate.Standard<MAPPED> = PropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).map(mappedTypeName) { k1, t1 -> convert.invoke(k1, mappedTypeName, t1) } })
}

private class OptionalPropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (ConfigProperty<*>) -> Unit, private val construct: (String, Boolean) -> ConfigProperty<TYPE?>) : PropertyDelegate<TYPE?> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty<TYPE?>> {

        val prop = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, ConfigProperty<TYPE?>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): ConfigProperty<TYPE?> = prop
        }
    }
}

private class ListPropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (ConfigProperty<*>) -> Unit, private val construct: (String, Boolean) -> ConfigProperty.Required<TYPE>) : PropertyDelegate.Required<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Required<TYPE>> {

        val prop = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, ConfigProperty.Required<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): ConfigProperty.Required<TYPE> = prop
        }
    }

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })
}