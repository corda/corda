package net.corda.mockias.io

import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.Signature
import java.security.SignatureException

class SignatureOutputStream(wrapped: OutputStream, val signature: Signature) : FilterOutputStream(wrapped) {
    @Throws(IOException::class)
    override fun write(data: Int) {
        try {
            signature.update(data.toByte())
        } catch (e: SignatureException) {
            throw IOException(e.message, e)
        }
        super.write(data)
    }

    @Throws(IOException::class)
    override fun write(data: ByteArray, offset: Int, length: Int) {
        try {
            signature.update(data, offset, length)
        } catch (e: SignatureException) {
            throw IOException(e.message, e)
        }
        super.out.write(data, offset, length)
    }

    @Throws(IOException::class)
    override fun flush() {
        super.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        super.close()
    }

    @Suppress("UNUSED")
    @Throws(SignatureException::class)
    fun sign(): ByteArray = signature.sign()
}
