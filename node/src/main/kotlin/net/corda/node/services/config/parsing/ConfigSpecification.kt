package net.corda.node.services.config.parsing

import com.typesafe.config.ConfigObject
import java.time.Duration
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class ConfigSpecification(name: String?) {

    private val properties = mutableSetOf<ConfigProperty<*>>()

    val schema: ConfigSchema by lazy {

        ConfigPropertySchema(name, properties)
    }

    fun long(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Long> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, ConfigProperty.Companion::long)

    fun boolean(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Boolean> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, ConfigProperty.Companion::boolean)

    fun double(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Double> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, ConfigProperty.Companion::double)

    fun string(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<String> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, ConfigProperty.Companion::string)

    fun duration(key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<Duration> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, ConfigProperty.Companion::duration)

    fun nestedObject(schema: ConfigSchema? = null, key: String? = null, sensitive: Boolean = false): PropertyDelegate.Standard<ConfigObject> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, { k, s -> ConfigProperty.nestedObject(k, schema, s) })

    fun <ENUM : Enum<ENUM>> enum(key: String? = null, enumClass: KClass<ENUM>, sensitive: Boolean = false): PropertyDelegate.Standard<ENUM> = PropertyDelegateImpl(key, sensitive, { properties.add(it) }, { k, s -> ConfigProperty.enum(k, enumClass, s) })
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

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, ConfigValidationError>): PropertyDelegate.Standard<MAPPED>
    }
}

private class PropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (ConfigProperty<*>) -> Unit, private val construct: (String, Boolean) -> ConfigProperty.Standard<TYPE>) : PropertyDelegate.Standard<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Standard<TYPE>> {

        return object : ReadOnlyProperty<Any?, ConfigProperty.Standard<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): ConfigProperty.Standard<TYPE> = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        }
    }

    override fun list(): PropertyDelegate.Required<List<TYPE>> = ListPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).list() })

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })

    override fun <MAPPED : Any> map(mappedTypeName: String, convert: (String, TYPE) -> Validated<MAPPED, ConfigValidationError>): PropertyDelegate.Standard<MAPPED> = PropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).map(mappedTypeName, convert) })
}

private class OptionalPropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (ConfigProperty<*>) -> Unit, private val construct: (String, Boolean) -> ConfigProperty<TYPE?>) : PropertyDelegate<TYPE?> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty<TYPE?>> {

        return object : ReadOnlyProperty<Any?, ConfigProperty<TYPE?>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): ConfigProperty<TYPE?> = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        }
    }
}

private class ListPropertyDelegateImpl<TYPE>(private val key: String?, private val sensitive: Boolean = false, private val addToProperties: (ConfigProperty<*>) -> Unit, private val construct: (String, Boolean) -> ConfigProperty.Required<TYPE>) : PropertyDelegate.Required<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, ConfigProperty.Required<TYPE>> {

        return object : ReadOnlyProperty<Any?, ConfigProperty.Required<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): ConfigProperty.Required<TYPE> = construct.invoke(key ?: property.name, sensitive).also(addToProperties)
        }
    }

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })
}