package net.corda.coretests.internal.services

import net.corda.core.internal.outputStream
import net.corda.core.internal.services.FixupService
import net.corda.core.node.services.AttachmentId
import net.corda.node.VersionInfo
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import kotlin.test.assertFailsWith

class FixupServiceTests {
    companion object {
        @JvmField
        val ID1 = AttachmentId.randomSHA256()
        @JvmField
        val ID2 = AttachmentId.randomSHA256()
        @JvmField
        val ID3 = AttachmentId.randomSHA256()
        @JvmField
        val ID4 = AttachmentId.randomSHA256()
    }

    @Test(timeout=300_000)
    fun `test fixup rule that adds attachment`() {
        val fixupJar = Files.createTempFile("fixup", ".jar")
                .writeFixupRules("$ID1 => $ID2, $ID3")
        val fixedIDs = with(newFixupService(fixupJar.toUri().toURL())) {
            fixupAttachmentIds(listOf(ID1))
        }
        assertThat(fixedIDs).containsExactly(ID2, ID3)
    }

    @Test(timeout=300_000)
    fun `test fixup rule that deletes attachment`() {
        val fixupJar = Files.createTempFile("fixup", ".jar")
                .writeFixupRules("$ID1 =>")
        val fixedIDs = with(newFixupService(fixupJar.toUri().toURL())) {
            fixupAttachmentIds(listOf(ID1))
        }
        assertThat(fixedIDs).isEmpty()
    }

    @Test(timeout=300_000)
    fun `test fixup rule with blank LHS`() {
        val fixupJar = Files.createTempFile("fixup", ".jar")
                .writeFixupRules(" => $ID2")
        val ex = assertFailsWith<IllegalArgumentException> {
            newFixupService(fixupJar.toUri().toURL()).fixupAttachmentIds(emptyList())
        }
        assertThat(ex).hasMessageContaining(
                "Forbidden empty list of source attachment IDs in '$fixupJar'"
        )
    }

    @Test(timeout=300_000)
    fun `test fixup rule without arrows`() {
        val rule = " $ID1 "
        val fixupJar = Files.createTempFile("fixup", ".jar")
                .writeFixupRules(rule)
        val ex = assertFailsWith<IllegalArgumentException> {
            newFixupService(fixupJar.toUri().toURL()).fixupAttachmentIds(emptyList())
        }
        assertThat(ex).hasMessageContaining(
                "Invalid fix-up line '${rule.trim()}' in '$fixupJar'"
        )
    }

    @Test(timeout=300_000)
    fun `test fixup rule with too many arrows`() {
        val rule = " $ID1 => $ID2 => $ID3 "
        val fixupJar = Files.createTempFile("fixup", ".jar")
                .writeFixupRules(rule)
        val ex = assertFailsWith<IllegalArgumentException> {
            newFixupService(fixupJar.toUri().toURL()).fixupAttachmentIds(emptyList())
        }
        assertThat(ex).hasMessageContaining(
                "Invalid fix-up line '${rule.trim()}' in '$fixupJar'"
        )
    }

    @Test(timeout=300_000)
    fun `test fixup file containing multiple rules and comments`() {
        val fixupJar = Files.createTempFile("fixup", ".jar").writeFixupRules(
                "# Whole line comment",
                "\t$ID1,$ID2 =>  $ID2,,  $ID3 # EOl comment",
                "   # Empty line with comment",
                "",
                "$ID3 => $ID4"
        )
        val fixedIDs = with(newFixupService(fixupJar.toUri().toURL())) {
            fixupAttachmentIds(listOf(ID2, ID1))
        }
        assertThat(fixedIDs).containsExactlyInAnyOrder(ID2, ID4)
    }

    private fun Path.writeFixupRules(vararg lines: String): Path {
        JarOutputStream(outputStream()).use { jar ->
            jar.setMethod(ZipEntry.DEFLATED)
            jar.setLevel(Deflater.NO_COMPRESSION)
            jar.putNextEntry(directoryEntry("META-INF"))
            jar.putNextEntry(fileEntry("META-INF/Corda-Fixups"))
            for (line in lines) {
                jar.write(line.toByteArray())
                jar.write('\r'.toInt())
                jar.write('\n'.toInt())
            }
        }
        return this
    }

    private fun directoryEntry(internalName: String): ZipEntry {
        return ZipEntry("$internalName/").apply {
            method = ZipEntry.STORED
            compressedSize = 0
            size = 0
            crc = 0
        }
    }

    private fun fileEntry(internalName: String): ZipEntry {
        return ZipEntry(internalName).apply {
            method = ZipEntry.DEFLATED
        }
    }

    private fun newFixupService(vararg urls: URL): FixupService {
        val loader = JarScanningCordappLoader.fromJarUrls(urls.toList(), VersionInfo.UNKNOWN)
        return FixupService(loader.appClassLoader)
    }
}
