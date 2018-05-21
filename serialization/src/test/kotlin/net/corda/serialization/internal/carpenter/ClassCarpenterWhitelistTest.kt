/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.serialization.internal.carpenter

import net.corda.core.serialization.ClassWhitelist
import net.corda.core.serialization.CordaSerializable
import org.assertj.core.api.Assertions
import org.junit.Ignore
import org.junit.Test
import java.io.NotSerializableException

class ClassCarpenterWhitelistTest {

    // whitelisting a class on the class path will mean we will carpente up a class that
    // contains it as a member
    @Test
    fun whitelisted() {
        data class A(val a: Int)

        class WL : ClassWhitelist {
            private val allowedClasses = setOf<String>(
                    A::class.java.name
            )

            override fun hasListed(type: Class<*>): Boolean = type.name in allowedClasses
        }

        val cc = ClassCarpenterImpl(whitelist = WL())

        // if this works, the test works, if it throws then we're in a world of pain, we could
        // go further but there are a lot of other tests that test weather we can build
        // carpented objects
        cc.build(ClassSchema("thing", mapOf("a" to NonNullableField(A::class.java))))
    }

    @Test
    @Ignore("Currently the carpenter doesn't inspect it's whitelist so will carpent anything" +
            "it's asked relying on the serializer factory to not ask for anything")
    fun notWhitelisted() {
        data class A(val a: Int)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) = false
        }

        val cc = ClassCarpenterImpl(whitelist = WL())

        // Class A isn't on the whitelist, so we should fail to carpent it
        Assertions.assertThatThrownBy {
            cc.build(ClassSchema("thing", mapOf("a" to NonNullableField(A::class.java))))
        }.isInstanceOf(NotSerializableException::class.java)
    }

    // despite now being whitelisted and on the class path, we will carpent this because
    // it's marked as CordaSerializable
    @Test
    fun notWhitelistedButAnnotated() {
        @CordaSerializable
        data class A(val a: Int)

        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) = false
        }

        val cc = ClassCarpenterImpl(whitelist = WL())

        // again, simply not throwing here is enough to show the test worked and the carpenter
        // didn't reject the type even though it wasn't on the whitelist because it was
        // annotated properly
        cc.build(ClassSchema("thing", mapOf("a" to NonNullableField(A::class.java))))
    }

    @Test
    @Ignore("Currently the carpenter doesn't inspect it's whitelist so will carpent anything" +
            "it's asked relying on the serializer factory to not ask for anything")
    fun notWhitelistedButCarpented() {
        // just have the white list reject *Everything* except ints
        class WL : ClassWhitelist {
            override fun hasListed(type: Class<*>) = type.name == "int"
        }

        val cc = ClassCarpenterImpl(whitelist = WL())

        val schema1a = ClassSchema("thing1a", mapOf("a" to NonNullableField(Int::class.java)))

        // thing 1 won't be set as corda serializable, meaning we won't build schema 2
        schema1a.unsetCordaSerializable()

        val clazz1a = cc.build(schema1a)
        val schema2 = ClassSchema("thing2", mapOf("a" to NonNullableField(clazz1a)))

        // thing 2 references thing 1 which wasn't carpented as corda s erializable and thus
        // this will fail
        Assertions.assertThatThrownBy {
            cc.build(schema2)
        }.isInstanceOf(NotSerializableException::class.java)

        // create a second type of schema1, this time leave it as corda serialzable
        val schema1b = ClassSchema("thing1b", mapOf("a" to NonNullableField(Int::class.java)))

        val clazz1b = cc.build(schema1b)

        // since schema 1b was created as CordaSerializable this will work
        ClassSchema("thing2", mapOf("a" to NonNullableField(clazz1b)))
    }
}