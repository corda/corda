package net.corda.node.services.config.parsing

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import java.time.Duration
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class ConfigSpecification<VALUE>(name: String?) : ConfigSchema, Configuration.Value.Parser<VALUE> {

    private val mutableProperties = mutableSetOf<Configuration.Property.Definition<*>>()

    override val properties: Set<Configuration.Property.Definition<*>> = mutableProperties

    private val schema: ConfigSchema by lazy {

        ConfigPropertySchema(name, properties)
    }

    fun long(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Long> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, Configuration.Property.Definition.Companion::long)

    fun boolean(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Boolean> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, Configuration.Property.Definition.Companion::boolean)

    fun double(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Double> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, Configuration.Property.Definition.Companion::double)

    fun string(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<String> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, Configuration.Property.Definition.Companion::string)

    fun duration(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Duration> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, Configuration.Property.Definition.Companion::duration)

    fun nestedObject(schema: ConfigSchema? = null, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ConfigObject> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, { k, s -> Configuration.Property.Definition.nestedObject(k, schema, s) })

    fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegateImpl(key, sensitive, { mutableProperties.add(it) }, { k, s -> Configuration.Property.Definition.enum(k, enumClass, s) })

    override val name: String? get() = schema.name

    override fun description() = schema.description()

    override fun validate(target: Config, options: Configuration.Validation.Options?) = schema.validate(target, options)

    override fun describe(configuration: Config) = schema.describe(configuration)
}

inline fun <reified ENUM : Enum<ENUM>, VALUE : Any> ConfigSpecification<VALUE>.enum(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = enum(key, ENUM::class, sensitive)

interface PropertyDelegate<TYPE> {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition<TYPE>>

    interface Required<TYPE> {

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Required<TYPE>>

        fun optional(defaultValue: TYPE? = null): PropertyDelegate<TYPE?>
    }

    interface Single<TYPE> {

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Single<TYPE>>

        fun list(): PropertyDelegate.Required<List<TYPE>>
    }

    interface Standard<TYPE> : PropertyDelegate.Required<TYPE>, PropertyDelegate.Single<TYPE> {

        override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>>

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): PropertyDelegate.Standard<MAPPED>
    }
}

inline fun <TYPE, reified MAPPED : Any> PropertyDelegate.Standard<TYPE>.map(noinline convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): PropertyDelegate.Standard<MAPPED> = map(MAPPED::class.java.simpleName, convert)

private class PropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (Configuration.Property.Definition<*>) -> Unit, private val construct: (String, Boolean) -> Configuration.Property.Definition.Standard<TYPE>) : PropertyDelegate.Standard<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>> {

        val prop = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property.Definition.Standard<TYPE> = prop
        }
    }

    override fun list(): PropertyDelegate.Required<List<TYPE>> = ListPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).list() })

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })

    override fun <MAPPED : Any> map(mappedTypeName: String, convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): PropertyDelegate.Standard<MAPPED> = PropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).map(mappedTypeName) { k1, t1 -> convert.invoke(k1, mappedTypeName, t1) } })
}

private class OptionalPropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (Configuration.Property.Definition<*>) -> Unit, private val construct: (String, Boolean) -> Configuration.Property.Definition<TYPE?>) : PropertyDelegate<TYPE?> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition<TYPE?>> {

        val prop = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, Configuration.Property.Definition<TYPE?>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property.Definition<TYPE?> = prop
        }
    }
}

private class ListPropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (Configuration.Property.Definition<*>) -> Unit, private val construct: (String, Boolean) -> Configuration.Property.Definition.Required<TYPE>) : PropertyDelegate.Required<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Required<TYPE>> {

        val prop = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, Configuration.Property.Definition.Required<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property.Definition.Required<TYPE> = prop
        }
    }

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })
}