package net.corda.nodeapi.internal.persistence.factory

import jakarta.persistence.Entity
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class JavaxPersistenceDetectionTest {
    @Entity
    class JakartaEntity

    @Test(timeout = 300_000)
    fun `a class built against jakarta persistence is not flagged`() {
        val bytes = JakartaEntity::class.java.classLoader
                .getResourceAsStream(JakartaEntity::class.java.name.replace('.', '/') + ".class")!!.use { it.readBytes() }
        assertThat(builtForJavaxPersistenceOnly(bytes)).isFalse()
    }

    @Test(timeout = 300_000)
    fun `a class file that refers to javax persistence only is flagged`() {
        // The constant pool of a class annotated with javax.persistence.Entity contains this descriptor.
        val bytes = "Êþº¾...Ljavax/persistence/Entity;...".toByteArray(Charsets.ISO_8859_1)
        assertThat(builtForJavaxPersistenceOnly(bytes)).isTrue()
    }

    @Test(timeout = 300_000)
    fun `a class file annotated for both persistence APIs is not flagged`() {
        // A CorDapp that runs on Corda 4.14 and 4.15 carries both annotation sets; the constant pool then contains both descriptors.
        val bytes = "Êþº¾...Ljavax/persistence/Entity;...Ljakarta/persistence/Entity;...".toByteArray(Charsets.ISO_8859_1)
        assertThat(builtForJavaxPersistenceOnly(bytes)).isFalse()
    }
}
