package com.r3.corda.enterprise.perftestcordapp

import net.corda.core.serialization.SerializationWhitelist
import java.util.*


class Whitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(LinkedList::class.java)
}