package net.corda.core.serialization.amqp.custom

import net.corda.core.serialization.amqp.CustomSerializer
import java.math.BigDecimal


object BigDecimalSerializer : CustomSerializer.ToString<BigDecimal>(BigDecimal::class.java)