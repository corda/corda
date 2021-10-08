package net.corda.core.internal.utilities

import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

object ZipBombDetector {

    private class CounterInputStream(source : InputStream) : FilterInputStream(source) {
        private var byteCount : Long = 0

        val count : Long
            get() = byteCount

        override fun read(): Int {
            return super.read().also { byte ->
                if(byte >= 0) byteCount += 1
            }
        }

        override fun read(b: ByteArray): Int {
            return super.read(b).also { bytesRead ->
                if(bytesRead > 0) byteCount += bytesRead
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            return super.read(b, off, len).also { bytesRead ->
                if(bytesRead > 0) byteCount += bytesRead
            }
        }
    }

    /**
     * Check if a zip file is a potential malicious zip bomb
     * @param source the zip archive file content
     * @param maxUncompressedSize the maximum allowable uncompressed archive size
     * @param maxCompressionRatio the maximum allowable compression ratio
     * @return true if the zip file total uncompressed size exceeds [maxUncompressedSize] and the
     * average entry compression ratio is larger than [maxCompressionRatio], false otherwise
     */
    @Suppress("NestedBlockDepth")
    fun scanZip(source : InputStream, maxUncompressedSize : Long, maxCompressionRatio : Float = 10.0f) : Boolean {
        val counterInputStream = CounterInputStream(source)
        var uncompressedByteCount : Long = 0
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        ZipInputStream(counterInputStream).use { zipInputStream ->
            while(true) {
                zipInputStream.nextEntry ?: break
                while(true) {
                    val read = zipInputStream.read(buffer)
                    if(read <= 0) break
                    uncompressedByteCount += read
                    if(uncompressedByteCount > maxUncompressedSize &&
                            uncompressedByteCount.toFloat() / counterInputStream.count.toFloat() > maxCompressionRatio) {
                        return true
                    }
                }
            }
        }
        return false
    }
}