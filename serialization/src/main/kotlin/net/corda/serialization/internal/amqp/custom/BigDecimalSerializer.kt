package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import java.math.BigDecimal

/**
 * A serializer for [BigDecimal], utilising the string based helper.  [BigDecimal] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigDecimal.toString].
 */
object BigDecimalSerializer : CustomSerializer.ToString<BigDecimal>(BigDecimal::class.java)