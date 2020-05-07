package net.corda.core.internal

import com.nhaarman.mockito_kotlin.mock
import net.corda.core.contracts.ContractAttachment
import net.corda.core.contracts.ContractClassName
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory
import net.corda.core.serialization.internal.AttachmentsClassLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Ignore
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.net.URL
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

    @Test(timeout=300_000)
	fun predicateClassAreLoadedSuccessfully() {
        val classes = createInstancesOfClassesImplementing(BaseInterface::class.java.classLoader, BaseInterface::class.java)

        val classNames = classes.map { it.javaClass.name }

        assertThat(classNames)
            .contains(ConcreteClassWithEmptyConstructor::class.java.name)
            .doesNotContain(AbstractClass::class.java.name)
    }

    @Test(expected = IllegalArgumentException::class,timeout=300_000)
    fun throwsExceptionWhenClassDoesNotContainProperConstructors() {
        createInstancesOfClassesImplementing(BaseInterface::class.java.classLoader, BaseInterface2::class.java)
    }

    @Test(timeout=300_000)
	fun `thread context class loader is adjusted, during the function execution`() {
        val result = executeWithThreadContextClassLoader(temporaryClassLoader) {
            assertThat(Thread.currentThread().contextClassLoader).isEqualTo(temporaryClassLoader)
            true
        }

        assertThat(result).isTrue()
        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(temporaryClassLoader)
    }

    @Test(timeout=300_000)
	fun `thread context class loader is set to the initial, even in case of a failure`() {
        assertThatThrownBy { executeWithThreadContextClassLoader(temporaryClassLoader) {
            throw RuntimeException()
        } }.isInstanceOf(RuntimeException::class.java)

        assertThat(Thread.currentThread().contextClassLoader).isNotEqualTo(temporaryClassLoader)
    }

    @Test(timeout=300_000)
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

    @Ignore("Using System.gc in this test which has no guarantees when/if gc occurs.")
    @Test(timeout=300_000)
    @Suppress("ExplicitGarbageCollectionCall", "UNUSED_VALUE")
    fun `test weak reference removed from map`() {
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
        var url: URL? = AttachmentURLStreamHandlerFactory.toUrl(attachment)

        val referenceQueue: ReferenceQueue<URL> = ReferenceQueue()
        val weakReference = WeakReference<URL>(url, referenceQueue)

        assertEquals(1, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())
        // Clear strong reference
        url = null
        System.gc()
        val ref = referenceQueue.remove(100000)
        assertSame(weakReference, ref)
        assertEquals(0, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())
    }

    @Ignore("Using System.gc in this test which has no guarantees when/if gc occurs.")
    @Test(timeout=300_000)
    @Suppress("ExplicitGarbageCollectionCall", "UNUSED_VALUE")
    fun `test adding same attachment twice then removing`() {
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
        val attachment1 = signedAttachment(jarData)
        val attachment2 = signedAttachment(jarData)
        var url1: URL? = AttachmentURLStreamHandlerFactory.toUrl(attachment1)
        var url2: URL? = AttachmentURLStreamHandlerFactory.toUrl(attachment2)

        val referenceQueue1: ReferenceQueue<URL> = ReferenceQueue()
        val weakReference1 = WeakReference<URL>(url1, referenceQueue1)

        val referenceQueue2: ReferenceQueue<URL> = ReferenceQueue()
        val weakReference2 = WeakReference<URL>(url2, referenceQueue2)

        assertEquals(1, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())
        url1 = null
        System.gc()
        val ref1 = referenceQueue1.remove(500)
        assertNull(ref1)
        assertEquals(1, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())

        url2 = null
        System.gc()
        val ref2 = referenceQueue2.remove(100000)
        assertSame(weakReference2, ref2)
        assertSame(weakReference1, referenceQueue1.poll())
        assertEquals(0, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())
    }

    @Ignore("Using System.gc in this test which has no guarantees when/if gc occurs.")
    @Test(timeout=300_000)
    @Suppress("ExplicitGarbageCollectionCall", "UNUSED_VALUE")
    fun `test adding two different attachments then removing`() {
        val jarData1 = with(ByteArrayOutputStream()) {
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

        val attachment1 = signedAttachment(jarData1)
        val attachment2 = signedAttachment(jarData1, id = SecureHash.randomSHA256())
        var url1: URL? = AttachmentURLStreamHandlerFactory.toUrl(attachment1)
        var url2: URL? = AttachmentURLStreamHandlerFactory.toUrl(attachment2)

        val referenceQueue1: ReferenceQueue<URL> = ReferenceQueue()
        val weakReference1 = WeakReference<URL>(url1, referenceQueue1)

        val referenceQueue2: ReferenceQueue<URL> = ReferenceQueue()
        val weakReference2 = WeakReference<URL>(url2, referenceQueue2)

        assertEquals(2, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())
        url1 = null
        System.gc()
        val ref1 = referenceQueue1.remove(100000)
        assertSame(weakReference1, ref1)
        assertEquals(1, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())

        url2 = null
        System.gc()
        val ref2 = referenceQueue2.remove(100000)
        assertSame(weakReference2, ref2)
        assertEquals(0, AttachmentURLStreamHandlerFactory.loadedAttachmentsSize())
    }

    private fun signedAttachment(data: ByteArray, id: AttachmentId = contractAttachmentId,
                                 vararg parties: Party) = ContractAttachment.create(
        object : AbstractAttachment({ data }, "test") {
            override val id: SecureHash get() = id

            override val signerKeys: List<PublicKey> get() = parties.map(Party::owningKey)
        }, PROGRAM_ID, signerKeys = parties.map(Party::owningKey)
    )
}

// Our dummy attachment will contain a class that is created from this one.
// This is because our attachment must contain a class that DOES NOT exist
// inside the application classloader.
class TemplateClassWithEmptyConstructor : ClassLoadingUtilsTest.BaseInterface
