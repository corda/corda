package com.r3.corda.networkmanage.common.utils

import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

inline fun <reified T : Any> InputStream.readObject(): T {
    DataInputStream(this).run {
        val messageSize = this.readInt()
        val messageBytes = ByteArray(messageSize)
        this.read(messageBytes)
        return messageBytes.deserialize()
    }
}

fun OutputStream.writeObject(message: Any) {
    DataOutputStream(this).run {
        val messageBytes = message.serialize().bytes
        this.writeInt(messageBytes.size)
        this.write(messageBytes)
        this.flush()
    }
}