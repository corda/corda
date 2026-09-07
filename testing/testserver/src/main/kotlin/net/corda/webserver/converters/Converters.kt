package net.corda.webserver.converters

import jakarta.ws.rs.ext.ParamConverter
import jakarta.ws.rs.ext.ParamConverterProvider
import jakarta.ws.rs.ext.Provider
import net.corda.core.identity.CordaX500Name
import java.lang.reflect.Type

object CordaX500NameConverter : ParamConverter<CordaX500Name> {
    override fun toString(value: CordaX500Name) = value.toString()
    override fun fromString(value: String) = CordaX500Name.parse(value)
}

@Provider
object CordaConverterProvider : ParamConverterProvider {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getConverter(rawType: Class<T>, genericType: Type?, annotations: Array<out Annotation>?): ParamConverter<T>? {
        if (rawType == CordaX500Name::class.java) {
            return CordaX500NameConverter as ParamConverter<T>?
        }
        return null
    }
}
