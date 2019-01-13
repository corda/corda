package net.corda.vega.plugin.customserializers

import com.opengamma.strata.basics.currency.Currency
import net.corda.core.serialization.SerializationCustomSerializer

@Suppress("UNUSED")
class CurrencySerializer : SerializationCustomSerializer<Currency, CurrencySerializer.Proxy> {
    data class Proxy(val currency: String)

    override fun fromProxy(proxy: Proxy): Currency {
        return withCurrentClassLoader { Currency.parse(proxy.currency) }
    }
    override fun toProxy(obj: Currency) = Proxy(obj.toString())

    /**
     * The initialization of [Currency] uses the classpath to identify needed resources.
     * However, it gives priority to the classloader in the thread context over the one this class was loaded with.
     * See: [com.opengamma.strata.collect.io.ResourceLocator.classLoader]
     *
     * This is the reason we temporarily override the class loader in the thread context here, with the classloader of this
     * class, which is guaranteed to contain everything in the 3rd party library's classpath.
     */
    private fun withCurrentClassLoader(serializationFunction: () -> Currency): Currency {
        val threadClassLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = this.javaClass.classLoader
        val result =serializationFunction()
        Thread.currentThread().contextClassLoader = threadClassLoader
        return result
    }
}
