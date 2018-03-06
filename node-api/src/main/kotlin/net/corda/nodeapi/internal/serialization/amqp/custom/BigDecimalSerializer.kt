/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.nodeapi.internal.serialization.amqp.custom

import net.corda.nodeapi.internal.serialization.amqp.CustomSerializer
import java.math.BigDecimal

/**
 * A serializer for [BigDecimal], utilising the string based helper.  [BigDecimal] seems to have no import/export
 * features that are precision independent other than via a string.  The format of the string is discussed in the
 * documentation for [BigDecimal.toString].
 */
object BigDecimalSerializer : CustomSerializer.ToString<BigDecimal>(BigDecimal::class.java)