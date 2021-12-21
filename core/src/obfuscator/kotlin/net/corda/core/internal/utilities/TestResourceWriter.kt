package net.corda.core.internal.utilities

import net.corda.core.obfuscator.XorOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TestResourceWriter {

    private val externalZipBombUrls = arrayOf(
        URL("https://www.bamsoftware.com/hacks/zipbomb/zbsm.zip"),
        URL("https://www.bamsoftware.com/hacks/zipbomb/zblg.zip"),
        URL("https://www.bamsoftware.com/hacks/zipbomb/zbxl.zip")
    )

    @JvmStatic
    @Suppress("NestedBlockDepth", "MagicNumber")
    fun main(vararg args : String) {
        for(arg in args) {
            /**
             * Download zip bombs
             */
            for(url in externalZipBombUrls) {
                url.openStream().use { inputStream ->
                    val destination = Paths.get(arg).resolve(Paths.get(url.path +  ".xor").fileName)
                    Files.newOutputStream(destination).buffered().let(::XorOutputStream).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
            /**
             * Create a jar archive with a huge manifest file, used in unit tests to check that it is also identified as a zip bomb.
             * This is because {@link java.util.jar.JarInputStream}
             * <a href="https://github.com/openjdk/jdk/blob/4dedba9ebe11750f4b39c41feb4a4314ccdb3a08/src/java.base/share/classes/java/util/jar/JarInputStream.java#L95">eagerly loads the manifest file in memory</a>
             * which would make such a jar dangerous if used as an attachment
             */
            val destination = Paths.get(arg).resolve(Paths.get("big-manifest.jar.xor").fileName)
            ZipOutputStream(XorOutputStream((Files.newOutputStream(destination).buffered()))).use { zos ->
                val zipEntry = ZipEntry("MANIFEST.MF")
                zipEntry.method = ZipEntry.DEFLATED
                zos.putNextEntry(zipEntry)
                val buffer = ByteArray(0x100000) { 0x0 }
                var written = 0L
                while(written < 10_000_000_000) {
                    zos.write(buffer)
                    written += buffer.size
                }
                zos.closeEntry()
            }
        }
    }
}