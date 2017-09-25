package net.corda.nodeapi.internal.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.ClosureSerializer
import java.io.Serializable

object CordaClosureSerializer : ClosureSerializer() {
    val ERROR_MESSAGE = "Unable to serialize Java Lambda expression, unless explicitly declared e.g., Runnable r = (Runnable & Serializable) () -> System.out.println(\"Hello world!\");"

    override fun write(kryo: Kryo, output: Output, target: Any) {
        if (!isSerializable(target)) {
            throw IllegalArgumentException(ERROR_MESSAGE)
        }
        super.write(kryo, output, target)
    }

    private fun isSerializable(target: Any): Boolean {
        return target is Serializable
    }
}