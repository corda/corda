package net.corda.serialization.internal.amqp.custom

import net.corda.serialization.internal.amqp.CustomSerializer
import java.math.BigInteger

/**
 * A serializer for [BigInteger], utilising the string based helper.  [BigInteger] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigInteger.toString].
 */
object BigIntegerSerializer : CustomSerializer.ToString<BigInteger>(BigInteger::class.java)