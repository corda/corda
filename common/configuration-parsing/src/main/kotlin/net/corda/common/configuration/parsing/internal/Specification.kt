package net.corda.common.configuration.parsing.internal

import com.typesafe.config.ConfigObject
import java.time.Duration
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface PropertyDelegate<TYPE> {

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition<TYPE>>

    interface Required<TYPE> {

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Required<TYPE>>

        fun optional(defaultValue: TYPE? = null): PropertyDelegate<TYPE?>
    }

    interface Single<TYPE> {

        operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Single<TYPE>>

        fun list(): Required<List<TYPE>>
    }

    interface Standard<TYPE> : Required<TYPE>, Single<TYPE> {

        override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>>

        fun <MAPPED : Any> mapValid(mappedTypeName: String, convert: (TYPE) -> Valid<MAPPED>): Standard<MAPPED>

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (TYPE) -> MAPPED): Standard<MAPPED> = mapValid(mappedTypeName) { value -> valid(convert.invoke(value)) }
    }

    companion object {

        internal fun long(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<Long> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::long)

        internal fun int(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<Int> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::int)

        internal fun boolean(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<Boolean> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::boolean)

        internal fun double(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<Double> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::double)

        internal fun float(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<Float> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::float)

        internal fun string(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<String> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::string)

        internal fun duration(key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<Duration> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, Configuration.Property.Definition.Companion::duration)

        internal fun nestedObject(schema: Configuration.Schema?, key: String?, prefix: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<ConfigObject> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, { k, s -> Configuration.Property.Definition.nestedObject(k, schema, s) })

        internal fun <ENUM : Enum<ENUM>> enum(key: String?, prefix: String?, enumClass: KClass<ENUM>, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): Standard<ENUM> = PropertyDelegateImpl(key, prefix, sensitive, addProperty, { k, s -> Configuration.Property.Definition.enum(k, enumClass, s) })
    }
}

private class PropertyDelegateImpl<TYPE>(private val key: String?, private val prefix: String?, private val sensitive: Boolean = false, private val addToProperties: (Configuration.Property.Definition<*>) -> Unit, private val construct: (String, Boolean) -> Configuration.Property.Definition.Standard<TYPE>) : PropertyDelegate.Standard<TYPE> {

    override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>> {

        val shortName = key ?: property.name
        val prop = construct.invoke(prefix?.let { "$prefix.$shortName" } ?: shortName, sensitive).also(addToProperties)
        return object : ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>> {

            override fun getValue(thisRef: Any?, property: KProperty<*>): Configuration.Property.Definition.Standard<TYPE> = prop
        }
    }

    override fun list(): PropertyDelegate.Required<List<TYPE>> = ListPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).list() })

    override fun optional(defaultValue: TYPE?): PropertyDelegate<TYPE?> = OptionalPropertyDelegateImpl(key, sensitive, addToProperties, { k, s -> construct.invoke(k, s).optional(defaultValue) })

    override fun <MAPPED : Any> mapValid(mappedTypeName: String, convert: (TYPE) -> Valid<MAPPED>): PropertyDelegate.Standard<MAPPED> = PropertyDelegateImpl(key, prefix, sensitive, addToProperties, { k, s -> construct.invoke(k, s).mapValid(mappedTypeName) { value -> convert.invoke(value) } })
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