package net.corda.webserver.converters

import net.corda.core.identity.CordaX500Name
import java.lang.reflect.Type
import javax.ws.rs.ext.ParamConverter
import javax.ws.rs.ext.ParamConverterProvider
import javax.ws.rs.ext.Provider

object CordaX500NameConverter : ParamConverter<CordaX500Name> {
    override fun toString(value: CordaX500Name) = value.toString()
    override fun fromString(value: String) = CordaX500Name.parse(value)
}

@Provider
object CordaConverterProvider : ParamConverterProvider {
    override fun <T : Any> getConverter(rawType: Class<T>, genericType: Type?, annotations: Array<out Annotation>?): ParamConverter<T>? {
        if (rawType == CordaX500Name::class.java) {
            @Suppress("UNCHECKED_CAST")
            return CordaX500NameConverter as ParamConverter<T>
        }
        return null
    }
}