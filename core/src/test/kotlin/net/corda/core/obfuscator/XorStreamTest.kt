package net.corda.core.obfuscator

import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*

@RunWith(Parameterized::class)
class XorStreamTest(private val size : Int) {
    private val random = Random(0)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun generateTestCases(): Collection<*> {
            return listOf(0, 16, 31, 127, 1000, 1024)
        }
    }

    @Test(timeout = 5000)
    fun test() {
        val baos = ByteArrayOutputStream(size)
        val md = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        DigestOutputStream(XorOutputStream(baos), md).use { outputStream ->
            var written = 0
            while(written < size) {
                random.nextBytes(buffer)
                val bytesToWrite = (size - written).coerceAtMost(buffer.size)
                outputStream.write(buffer, 0, bytesToWrite)
                written += bytesToWrite
            }
        }
        val digest = md.digest()
        md.reset()
        DigestInputStream(XorInputStream(ByteArrayInputStream(baos.toByteArray())), md).use { inputStream ->
            while(true) {
                val read = inputStream.read(buffer)
                if(read <= 0) break
            }
        }
        Assert.assertArrayEquals(digest, md.digest())
    }
}