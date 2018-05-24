/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal

import com.esotericsoftware.kryo.*
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.util.DefaultClassResolver
import com.esotericsoftware.kryo.util.MapReferenceResolver
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.internal.DEPLOYED_CORDAPP_UPLOADER
import net.corda.core.node.services.AttachmentStorage
import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializationContext
import net.corda.node.serialization.kryo.CordaClassResolver
import net.corda.node.serialization.kryo.CordaKryo
import net.corda.node.serialization.kryo.kryoMagic
import net.corda.testing.internal.rigorousMock
import net.corda.testing.services.MockAttachmentStorage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.lang.IllegalStateException
import java.sql.Connection
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@CordaSerializable
enum class Foo {
    Bar {
        override val value = 0
    },
    Stick {
        override val value = 1
    };

    abstract val value: Int
}

enum class BadFood {
    Mud {
        override val value = -1
    };

    abstract val value: Int
}

@CordaSerializable
enum class Simple {
    Easy
}

enum class BadSimple {
    Nasty
}

@CordaSerializable
open class Element

open class SubElement : Element()

class SubSubElement : SubElement()

abstract class AbstractClass

interface Interface

@CordaSerializable
interface SerializableInterface

interface SerializableSubInterface : SerializableInterface

class NotSerializable

class SerializableViaInterface : SerializableInterface

open class SerializableViaSubInterface : SerializableSubInterface

class SerializableViaSuperSubInterface : SerializableViaSubInterface()

@CordaSerializable
class CustomSerializable : KryoSerializable {
    override fun read(kryo: Kryo?, input: Input?) {
    }

    override fun write(kryo: Kryo?, output: Output?) {
    }
}

@CordaSerializable
@DefaultSerializer(DefaultSerializableSerializer::class)
class DefaultSerializable

class DefaultSerializableSerializer : Serializer<DefaultSerializable>() {
    override fun write(kryo: Kryo, output: Output, obj: DefaultSerializable) {
    }

    override fun read(kryo: Kryo, input: Input, type: Class<DefaultSerializable>): DefaultSerializable {
        return DefaultSerializable()
    }
}

object EmptyWhitelist : ClassWhitelist {
    override fun hasListed(type: Class<*>): Boolean = false
}

class CordaClassResolverTests {
    private companion object {
        val emptyListClass = listOf<Any>().javaClass
        val emptySetClass = setOf<Any>().javaClass
        val emptyMapClass = mapOf<Any, Any>().javaClass
    }

    private val emptyWhitelistContext: SerializationContext = SerializationContextImpl(kryoMagic, this.javaClass.classLoader, EmptyWhitelist, emptyMap(), true, SerializationContext.UseCase.P2P, null)
    private val allButBlacklistedContext: SerializationContext = SerializationContextImpl(kryoMagic, this.javaClass.classLoader, AllButBlacklisted, emptyMap(), true, SerializationContext.UseCase.P2P, null)
    @Test
    fun `Annotation on enum works for specialised entries`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(Foo.Bar::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Unannotated specialised enum does not work`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(BadFood.Mud::class.java)
    }

    @Test
    fun `Annotation on simple enum works`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(Simple.Easy::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Unannotated simple enum does not work`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(BadSimple.Nasty::class.java)
    }

    @Test
    fun `Annotation on array element works`() {
        val values = arrayOf(Element())
        CordaClassResolver(emptyWhitelistContext).getRegistration(values.javaClass)
    }

    @Test(expected = KryoException::class)
    fun `Unannotated array elements do not work`() {
        val values = arrayOf(NotSerializable())
        CordaClassResolver(emptyWhitelistContext).getRegistration(values.javaClass)
    }

    @Test
    fun `Annotation not needed on abstract class`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(AbstractClass::class.java)
    }

    @Test
    fun `Annotation not needed on interface`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(Interface::class.java)
    }

    @Test
    fun `Calling register method on modified Kryo does not consult the whitelist`() {
        val kryo = CordaKryo(CordaClassResolver(emptyWhitelistContext))
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Calling register method on unmodified Kryo does consult the whitelist`() {
        val kryo = Kryo(CordaClassResolver(emptyWhitelistContext), MapReferenceResolver())
        kryo.register(NotSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation is needed without whitelisting`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation is not needed with whitelisting`() {
        val resolver = CordaClassResolver(emptyWhitelistContext.withWhitelisted(NotSerializable::class.java))
        resolver.getRegistration(NotSerializable::class.java)
    }

    @Test
    fun `Annotation not needed on Object`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(Object::class.java)
    }

    @Test
    fun `Annotation not needed on primitive`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(Integer.TYPE)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work for custom serializable`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(CustomSerializable::class.java)
    }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with Kryo annotation`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(DefaultSerializable::class.java)
    }

    private fun importJar(storage: AttachmentStorage, uploader: String = DEPLOYED_CORDAPP_UPLOADER) = AttachmentsClassLoaderTests.ISOLATED_CONTRACTS_JAR_PATH.openStream().use { storage.importAttachment(it, uploader, "") }

    @Test(expected = KryoException::class)
    fun `Annotation does not work in conjunction with AttachmentClassLoader annotation`() {
        val storage = MockAttachmentStorage()
        val attachmentHash = importJar(storage)
        val classLoader = AttachmentsClassLoader(arrayOf(attachmentHash).map { storage.openAttachment(it)!! })
        val attachedClass = Class.forName("net.corda.finance.contracts.isolated.AnotherDummyContract", true, classLoader)
        CordaClassResolver(emptyWhitelistContext).getRegistration(attachedClass)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Attempt to load contract attachment with the incorrect uploader should fails with IAE`() {
        val storage = MockAttachmentStorage()
        val attachmentHash = importJar(storage, "some_uploader")
        val classLoader = AttachmentsClassLoader(arrayOf(attachmentHash).map { storage.openAttachment(it)!! })
        val attachedClass = Class.forName("net.corda.finance.contracts.isolated.AnotherDummyContract", true, classLoader)
        CordaClassResolver(emptyWhitelistContext).getRegistration(attachedClass)
    }

    @Test
    fun `Annotation is inherited from interfaces`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(SerializableViaInterface::class.java)
        CordaClassResolver(emptyWhitelistContext).getRegistration(SerializableViaSubInterface::class.java)
    }

    @Test
    fun `Annotation is inherited from superclass`() {
        CordaClassResolver(emptyWhitelistContext).getRegistration(SubElement::class.java)
        CordaClassResolver(emptyWhitelistContext).getRegistration(SubSubElement::class.java)
        CordaClassResolver(emptyWhitelistContext).getRegistration(SerializableViaSuperSubInterface::class.java)
    }

    // Blacklist tests. Note: leave the variable public or else expected messages do not work correctly
    @get:Rule
    val expectedEx = ExpectedException.none()!!

    @Test
    fun `Check blacklisted class`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("Class java.util.HashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // HashSet is blacklisted.
        resolver.getRegistration(HashSet::class.java)
    }

    @Test
    fun `Kotlin EmptyList not registered`() {
        val resolver = CordaClassResolver(allButBlacklistedContext)
        assertNull(resolver.getRegistration(emptyListClass))
    }

    @Test
    fun `Kotlin EmptyList registers as Java emptyList`() {
        val javaEmptyListClass = Collections.emptyList<Any>().javaClass
        val kryo = rigorousMock<Kryo>()
        val resolver = CordaClassResolver(allButBlacklistedContext).apply { setKryo(kryo) }
        doReturn(DefaultSerializableSerializer()).whenever(kryo).getDefaultSerializer(javaEmptyListClass)
        doReturn(false).whenever(kryo).references
        doReturn(false).whenever(kryo).references = any()
        val registration = resolver.registerImplicit(emptyListClass)
        assertNotNull(registration)
        assertEquals(javaEmptyListClass, registration.type)
        assertEquals(DefaultClassResolver.NAME.toInt(), registration.id)
        verify(kryo).getDefaultSerializer(javaEmptyListClass)
        assertEquals(registration, resolver.getRegistration(emptyListClass))
    }

    @Test
    fun `Kotlin EmptySet not registered`() {
        val resolver = CordaClassResolver(allButBlacklistedContext)
        assertNull(resolver.getRegistration(emptySetClass))
    }

    @Test
    fun `Kotlin EmptySet registers as Java emptySet`() {
        val javaEmptySetClass = Collections.emptySet<Any>().javaClass
        val kryo = rigorousMock<Kryo>()
        val resolver = CordaClassResolver(allButBlacklistedContext).apply { setKryo(kryo) }
        doReturn(DefaultSerializableSerializer()).whenever(kryo).getDefaultSerializer(javaEmptySetClass)
        doReturn(false).whenever(kryo).references
        doReturn(false).whenever(kryo).references = any()
        val registration = resolver.registerImplicit(emptySetClass)
        assertNotNull(registration)
        assertEquals(javaEmptySetClass, registration.type)
        assertEquals(DefaultClassResolver.NAME.toInt(), registration.id)
        verify(kryo).getDefaultSerializer(javaEmptySetClass)
        assertEquals(registration, resolver.getRegistration(emptySetClass))
    }

    @Test
    fun `Kotlin EmptyMap not registered`() {
        val resolver = CordaClassResolver(allButBlacklistedContext)
        assertNull(resolver.getRegistration(emptyMapClass))
    }

    @Test
    fun `Kotlin EmptyMap registers as Java emptyMap`() {
        val javaEmptyMapClass = Collections.emptyMap<Any, Any>().javaClass
        val kryo = rigorousMock<Kryo>()
        val resolver = CordaClassResolver(allButBlacklistedContext).apply { setKryo(kryo) }
        doReturn(DefaultSerializableSerializer()).whenever(kryo).getDefaultSerializer(javaEmptyMapClass)
        doReturn(false).whenever(kryo).references
        doReturn(false).whenever(kryo).references = any()
        val registration = resolver.registerImplicit(emptyMapClass)
        assertNotNull(registration)
        assertEquals(javaEmptyMapClass, registration.type)
        assertEquals(DefaultClassResolver.NAME.toInt(), registration.id)
        verify(kryo).getDefaultSerializer(javaEmptyMapClass)
        assertEquals(registration, resolver.getRegistration(emptyMapClass))
    }

    open class SubHashSet<E> : HashSet<E>()

    @Test
    fun `Check blacklisted subclass`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.serialization.internal.CordaClassResolverTests\$SubHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // SubHashSet extends the blacklisted HashSet.
        resolver.getRegistration(SubHashSet::class.java)
    }

    class SubSubHashSet<E> : SubHashSet<E>()

    @Test
    fun `Check blacklisted subsubclass`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.serialization.internal.CordaClassResolverTests\$SubSubHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // SubSubHashSet extends SubHashSet, which extends the blacklisted HashSet.
        resolver.getRegistration(SubSubHashSet::class.java)
    }

    class ConnectionImpl(private val connection: Connection) : Connection by connection

    @Test
    fun `Check blacklisted interface impl`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superinterface java.sql.Connection of net.corda.serialization.internal.CordaClassResolverTests\$ConnectionImpl is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // ConnectionImpl implements blacklisted Connection.
        resolver.getRegistration(ConnectionImpl::class.java)
    }

    interface SubConnection : Connection
    class SubConnectionImpl(private val subConnection: SubConnection) : SubConnection by subConnection

    @Test
    fun `Check blacklisted super-interface impl`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superinterface java.sql.Connection of net.corda.serialization.internal.CordaClassResolverTests\$SubConnectionImpl is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // SubConnectionImpl implements SubConnection, which extends the blacklisted Connection.
        resolver.getRegistration(SubConnectionImpl::class.java)
    }

    @Test
    fun `Check forcibly allowed`() {
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // LinkedHashSet is allowed for serialization.
        resolver.getRegistration(LinkedHashSet::class.java)
    }

    @CordaSerializable
    class CordaSerializableHashSet<E> : HashSet<E>()

    @Test
    fun `Check blacklist precedes CordaSerializable`() {
        expectedEx.expect(IllegalStateException::class.java)
        expectedEx.expectMessage("The superclass java.util.HashSet of net.corda.serialization.internal.CordaClassResolverTests\$CordaSerializableHashSet is blacklisted, so it cannot be used in serialization.")
        val resolver = CordaClassResolver(allButBlacklistedContext)
        // CordaSerializableHashSet is @CordaSerializable, but extends the blacklisted HashSet.
        resolver.getRegistration(CordaSerializableHashSet::class.java)
    }
}