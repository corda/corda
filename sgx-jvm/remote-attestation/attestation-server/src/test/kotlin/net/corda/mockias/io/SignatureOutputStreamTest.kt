package net.corda.mockias.io

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.KeyPairGenerator
import java.security.Signature

class SignatureOutputStreamTest {

    private lateinit var output: ByteArrayOutputStream
    private lateinit var signedOutput: SignatureOutputStream
    private lateinit var reference: Signature

    @Before
    fun setup() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        val keyPair = keyPairGenerator.genKeyPair()
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
        }
        output = ByteArrayOutputStream()
        signedOutput = SignatureOutputStream(output, signature)
        reference = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
        }
    }

    @Test
    fun testSignValue() {
        signedOutput.write(-0x74)
        signedOutput.write(0x00)
        signedOutput.write(0x11)
        reference.update(ByteArrayOutputStream().let { baos ->
            baos.write(-0x74)
            baos.write(0x00)
            baos.write(0x11)
            baos.toByteArray()
        })
        assertArrayEquals(byteArrayOf(-0x74, 0x00, 0x11), output.toByteArray())
        assertArrayEquals(reference.sign(), signedOutput.sign())
    }

    @Test
    fun testSignBuffer() {
        val buffer = byteArrayOf(0x01, -0x7F, 0x64, -0x52, 0x00)
        signedOutput.write(buffer)
        reference.update(buffer)
        assertArrayEquals(buffer, output.toByteArray())
        assertArrayEquals(reference.sign(), signedOutput.sign())
    }
}