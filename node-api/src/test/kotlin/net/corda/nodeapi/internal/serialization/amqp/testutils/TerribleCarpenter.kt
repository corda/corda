package net.corda.nodeapi.internal.serialization.amqp.testutils

import net.corda.core.serialization.ClassWhitelist
import net.corda.nodeapi.internal.serialization.carpenter.ClassCarpenter
import net.corda.nodeapi.internal.serialization.carpenter.Schema

class TerribleCarpenter(
        cl: ClassLoader = Thread.currentThread().contextClassLoader,
        whitelist: ClassWhitelist,
        val e : Exception
) : ClassCarpenter(cl, whitelist) {
    override fun build(schema: Schema): Class<*> {
        throw e.apply { fillInStackTrace() }
    }
}