package net.corda.nodeapi.internal.serialization.kryo

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.util.Pool

fun interface KryoFactory {
    fun create(): Kryo
}

class KryoPool(val factory: KryoFactory) : Pool<Kryo>(true, true) {
    override fun create(): Kryo {
        return factory.create()
    }

    fun <T> run(task: Kryo.()->T): T {
        val kryo: Kryo = obtain()
        return try {
            kryo.task()
        } finally {
            free(kryo)
        }
    }
}
