package net.corda.node.services.config.parsing

import com.typesafe.config.ConfigObject
import net.corda.node.services.config.parsing.common.validation.Validated
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

        fun list(): PropertyDelegate.Required<List<TYPE>>
    }

    interface Standard<TYPE> : PropertyDelegate.Required<TYPE>, PropertyDelegate.Single<TYPE> {

        override operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): ReadOnlyProperty<Any?, Configuration.Property.Definition.Standard<TYPE>>

        fun <MAPPED : Any> map(mappedTypeName: String, convert: (key: String, typeName: String, TYPE) -> Validated<MAPPED, Configuration.Validation.Error>): PropertyDelegate.Standard<MAPPED>
    }

    companion object {

        internal fun long(key: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<Long> = PropertyDelegateImpl(key, sensitive, addProperty, Configuration.Property.Definition.Companion::long)

        internal fun boolean(key: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<Boolean> = PropertyDelegateImpl(key, sensitive, addProperty, Configuration.Property.Definition.Companion::boolean)

        internal fun double(key: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<Double> = PropertyDelegateImpl(key, sensitive, addProperty, Configuration.Property.Definition.Companion::double)

        internal fun string(key: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<String> = PropertyDelegateImpl(key, sensitive, addProperty, Configuration.Property.Definition.Companion::string)

        internal fun duration(key: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<Duration> = PropertyDelegateImpl(key, sensitive, addProperty, Configuration.Property.Definition.Companion::duration)

        internal fun nestedObject(schema: Configuration.Schema?, key: String?, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<ConfigObject> = PropertyDelegateImpl(key, sensitive, addProperty, { k, s -> Configuration.Property.Definition.nestedObject(k, schema, s) })

        internal fun <ENUM : Enum<ENUM>> enum(key: String?, enumClass: KClass<ENUM>, sensitive: Boolean, addProperty: (Configuration.Property.Definition<*>) -> Unit): PropertyDelegate.Standard<ENUM> = PropertyDelegateImpl(key, sensitive, addProperty, { k, s -> Configuration.Property.Definition.enum(k, enumClass, s) })
    }
}

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