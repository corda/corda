package net.corda.core.internal

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory
import net.corda.core.serialization.internal.AttachmentsClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.URLClassLoader
import java.security.PublicKey
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.Deflater.NO_COMPRESSION
import java.util.zip.ZipEntry
import java.util.zip.ZipEntry.DEFLATED
import java.util.zip.ZipEntry.STORED

class ClassLoadingUtilsTest {
    companion object {
        const val STANDALONE_CLASS_NAME = "com.example.StandaloneClassWithEmptyConstructor"
        const val PROGRAM_ID: ContractClassName = "net.corda.core.internal.DummyContract"
        val contractAttachmentId = SecureHash.randomSHA256()

        fun directoryEntry(internalName: String) = ZipEntry("$internalName/").apply {
            method = STORED
            compressedSize = 0
            size = 0
            crc = 0
        }

        fun classEntry(internalName: String) = ZipEntry("$internalName.class").apply {
            method = DEFLATED
        }

        init {
            // Register the "attachment://" URL scheme.
            // You may only register new schemes ONCE per JVM!
            AttachmentsClassLoader
        }
    }

    private val temporaryClassLoader = mock<ClassLoader>()

    interface BaseInterface

    interface BaseInterface2

    class ConcreteClassWithEmptyConstructor: BaseInterface

    abstract class AbstractClass: BaseInterface

    @Suppress("unused")
    class ConcreteClassWithNonEmptyConstructor(private val someData: Int): BaseInterface2

    @Test
    fun predicateClassAreLoadedSuccessfully() {
        val classes = createInstancesOfClassesImplementing(BaseInterface::class.java.classLoader, BaseInterface::class.java)

        val classNames = classes.map { it.javaClass.name }

        assertThat(classNames)
            .contains(ConcreteClassWithEmptyConstructor::class.java.name)
            .doesNotContain(AbstractClass::class.java.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsExceptionWhenClassDoesNotContainProperConstructors() {
        createInstancesOfClassesImplementing(BaseInterface::class.java.classLoader, BaseInterface2::class.java)
    }

    @Test
    fun `thread context class loader is adjusted, during the function execution`() {
        val result = executeWithThreadContextClassLoader(temporaryClassLoader) {
            assertThat(Thread.currentThread().contextClassLoader).isEqualTo(temporaryClassLoader)
            true
        }

        assertThat(result).isTrue()
        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(temporaryClassLoader)
    }

    @Test
    fun `thread context class loader is set to the initial, even in case of a failure`() {
        assertThatThrownBy { executeWithThreadContextClassLoader(temporaryClassLoader) {
            throw RuntimeException()
        } }.isInstanceOf(RuntimeException::class.java)

        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(temporaryClassLoader)
    }

    @Test
    fun `test locating classes inside attachment`() {
        val jarData = with(ByteArrayOutputStream()) {
            val internalName = STANDALONE_CLASS_NAME.asInternalName
            JarOutputStream(this, Manifest()).use {
                it.setLevel(NO_COMPRESSION)
                it.setMethod(DEFLATED)
                it.putNextEntry(directoryEntry("com"))
                it.putNextEntry(directoryEntry("com/example"))
                it.putNextEntry(classEntry(internalName))
                it.write(TemplateClassWithEmptyConstructor::class.java.renameTo(internalName))
            }
            toByteArray()
        }
        val attachment = signedAttachment(jarData)
        val url = AttachmentURLStreamHandlerFactory.toUrl(attachment)

        URLClassLoader(arrayOf(url)).use { cordappClassLoader ->
            val standaloneClass = createInstancesOfClassesImplementing(cordappClassLoader, BaseInterface::class.java)
                .map(Any::javaClass)
                .single()
            assertEquals(STANDALONE_CLASS_NAME, standaloneClass.name)
            assertEquals(cordappClassLoader, standaloneClass.classLoader)
        }
    }

    private fun signedAttachment(data: ByteArray, vararg parties: Party) = ContractAttachment.create(
        object : AbstractAttachment({ data }, "test") {
            override val id: SecureHash get() = contractAttachmentId

            override val signerKeys: List<PublicKey> get() = parties.map(Party::owningKey)
        }, PROGRAM_ID, signerKeys = parties.map(Party::owningKey)
    )
}

// Our dummy attachment will contain a class that is created from this one.
// This is because our attachment must contain a class that DOES NOT exist
// inside the application classloader.
class TemplateClassWithEmptyConstructor : ClassLoadingUtilsTest.BaseInterface
