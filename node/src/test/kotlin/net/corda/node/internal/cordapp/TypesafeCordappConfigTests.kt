/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.cordapp

import com.typesafe.config.ConfigFactory
import net.corda.core.cordapp.CordappConfigException
import org.junit.Test
import org.assertj.core.api.Assertions.assertThat

class TypesafeCordappConfigTests {
    @Test
    fun `test that all value types can be retrieved`() {
        val config = ConfigFactory.parseString("string=string\nint=1\nfloat=1.0\ndouble=1.0\nnumber=2\ndouble=1.01\nbool=false")
        val cordappConf = TypesafeCordappConfig(config)

        assertThat(cordappConf.get("string")).isEqualTo("string")
        assertThat(cordappConf.getString("string")).isEqualTo("string")
        assertThat(cordappConf.getInt("int")).isEqualTo(1)
        assertThat(cordappConf.getFloat("float")).isEqualTo(1.0F)
        assertThat(cordappConf.getDouble("double")).isEqualTo(1.01)
        assertThat(cordappConf.getNumber("number")).isEqualTo(2)
        assertThat(cordappConf.getBoolean("bool")).isEqualTo(false)
    }

    @Test
    fun `test a nested path`() {
        val config = ConfigFactory.parseString("outer: { inner: string }")
        val cordappConf = TypesafeCordappConfig(config)

        assertThat(cordappConf.getString("outer.inner")).isEqualTo("string")
    }

    @Test
    fun `test exists determines existence and lack of existence correctly`() {
        val config = ConfigFactory.parseString("exists=exists")
        val cordappConf = TypesafeCordappConfig(config)

        assertThat(cordappConf.exists("exists")).isTrue()
        assertThat(cordappConf.exists("notexists")).isFalse()
    }

    @Test(expected = CordappConfigException::class)
    fun `test that an exception is thrown when trying to access a non-extant field`() {
        val config = ConfigFactory.empty()
        val cordappConf = TypesafeCordappConfig(config)

        cordappConf.get("anything")
    }
}