/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.webserver.converters

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.uncheckedCast
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
            return uncheckedCast(CordaX500NameConverter)
        }
        return null
    }
}