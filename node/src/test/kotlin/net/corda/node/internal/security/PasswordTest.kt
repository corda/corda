/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.internal.security

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNot.not
import org.junit.Test

internal class PasswordTest {

    @Test
    fun immutability() {

        val charArray = "dadada".toCharArray()
        val password = Password(charArray)
        assertThat(password.value, equalTo(charArray))

        charArray[0] = 'm'
        assertThat(password.value, not(equalTo(charArray)))

        val value = password.value
        value[1] = 'e'
        assertThat(password.value, not(equalTo(value)))
    }

    @Test
    fun constructor_and_getters() {

        val value = "dadada"

        assertThat(Password(value.toCharArray()).value, equalTo(value.toCharArray()))
        assertThat(Password(value.toCharArray()).valueAsString, equalTo(value))

        assertThat(Password(value).value, equalTo(value.toCharArray()))
        assertThat(Password(value).valueAsString, equalTo(value))
    }

    @Test
    fun equals() {

        val passwordValue1 = Password("value1")
        val passwordValue2 = Password("value2")
        val passwordValue12 = Password("value1")

        assertThat(passwordValue1, equalTo(passwordValue1))

        assertThat(passwordValue1, not(equalTo(passwordValue2)))
        assertThat(passwordValue2, not(equalTo(passwordValue1)))

        assertThat(passwordValue1, equalTo(passwordValue12))
        assertThat(passwordValue12, equalTo(passwordValue1))
    }

    @Test
    fun hashcode() {

        val passwordValue1 = Password("value1")
        val passwordValue2 = Password("value2")
        val passwordValue12 = Password("value1")

        assertThat(passwordValue1.hashCode(), equalTo(passwordValue1.hashCode()))

        // not strictly required by hashCode() contract, but desirable
        assertThat(passwordValue1.hashCode(), not(equalTo(passwordValue2.hashCode())))
        assertThat(passwordValue2.hashCode(), not(equalTo(passwordValue1.hashCode())))

        assertThat(passwordValue1.hashCode(), equalTo(passwordValue12.hashCode()))
        assertThat(passwordValue12.hashCode(), equalTo(passwordValue1.hashCode()))
    }

    @Test
    fun close() {

        val value = "ipjd1@pijmps112112"
        val password = Password(value)

        password.use {
            val readValue = it.valueAsString
            assertThat(readValue, equalTo(value))
        }

        val readValue = password.valueAsString
        assertThat(readValue, not(equalTo(value)))
    }

    @Test
    fun toString_is_masked() {

        val value = "ipjd1@pijmps112112"
        val password = Password(value)

        val toString = password.toString()

        assertThat(toString, not(containsString(value)))
    }
}