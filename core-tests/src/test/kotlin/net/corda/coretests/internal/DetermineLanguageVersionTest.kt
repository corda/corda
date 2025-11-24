package net.corda.coretests.internal

import net.corda.core.contracts.Attachment
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.internal.cordapp.LanguageVersion
import net.corda.core.internal.verification.NodeVerificationSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.jar.JarInputStream
import java.security.PublicKey

class DetermineLanguageVersionTest {

    @Test(timeout = 300_000)
    fun `determineLanguageVersion returns correct metadata for Kotlin 1_2 jar`() {
        val jarBytes = javaClass.getResourceAsStream("/corda-finance-contracts-4.11.jar")!!.readBytes()
        val attachment = InMemoryAttachment(jarBytes)
        val lvBytecode = invokeDetermineLanguageVersion(attachment)
        assertThat(lvBytecode.classFileMajorVersion).isEqualTo(52)  // Java 8 (52) bytecode
        val kotlinMeta = lvBytecode.kotlinMetadataVersion
        assertThat(kotlinMeta).isNotNull
        // Kotlin 1.2 produces metadata version 1.1.x (major=1, minor=1, patch varies).
        assertThat(kotlinMeta!!.major).isEqualTo(1)
        assertThat(kotlinMeta.minor).isEqualTo(1)
    }

    @Test(timeout = 300_000)
    fun `determineLanguageVersion returns correct metadata for Kotlin 1_9 jar`() {
        val jarBytes = javaClass.getResourceAsStream("/corda-finance-contracts.jar")!!.readBytes()
        val attachment = InMemoryAttachment(jarBytes)
        val lvBytecode = invokeDetermineLanguageVersion(attachment)
        assertThat(lvBytecode.classFileMajorVersion).isEqualTo(61)  // Java 17 (61) bytecode
        val kotlinMeta = lvBytecode.kotlinMetadataVersion
        assertThat(kotlinMeta).isNotNull
        // Kotlin 1.9 produces metadata version 1.9.0 (major=1, minor=9, patch=0).
        assertThat(kotlinMeta!!.major).isEqualTo(1)
        assertThat(kotlinMeta.minor).isEqualTo(9)
    }

    @Test(timeout = 300_000)
    fun `determineLanguageVersion returns correct metadata for Java only jar`() {
        val jarBytes = javaClass.getResourceAsStream("/pure-java-legacy411.jar")!!.readBytes()
        val attachment = InMemoryAttachment(jarBytes)
        val lvBytecode = invokeDetermineLanguageVersion(attachment)
        assertThat(lvBytecode.classFileMajorVersion).isEqualTo(52)  // Java 8 (52) bytecode
        // No Kotlin metadata in this attachment.
        assertThat(lvBytecode.kotlinMetadataVersion).isNull()
    }

    private fun invokeDetermineLanguageVersion(attachment: Attachment): LanguageVersion.Bytecode {
        val method = NodeVerificationSupport::class.java
                .getDeclaredMethod("determineLanguageVersion", Attachment::class.java)
        method.isAccessible = true
        val instance = org.mockito.Mockito.mock(NodeVerificationSupport::class.java, org.mockito.Mockito.RETURNS_DEEP_STUBS)
        val languageVersion = method.invoke(instance, attachment) as LanguageVersion?
        assertThat(languageVersion).isInstanceOf(LanguageVersion.Bytecode::class.java)
        return languageVersion as LanguageVersion.Bytecode
    }

    private class InMemoryAttachment(private val content: ByteArray) : Attachment {
        override val id: SecureHash = SecureHash.sha256(content)
        override val signerKeys: List<PublicKey> = emptyList()
        @Deprecated("Use signerKeys", level = DeprecationLevel.HIDDEN)
        override val signers: List<Party> = emptyList()
        override val size: Int = content.size

        override fun open(): ByteArrayInputStream = ByteArrayInputStream(content)
        override fun openAsJAR(): JarInputStream = JarInputStream(ByteArrayInputStream(content))
    }
}
