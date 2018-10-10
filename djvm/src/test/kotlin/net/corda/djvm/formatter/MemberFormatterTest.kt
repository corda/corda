package net.corda.djvm.formatter

import net.corda.djvm.formatting.MemberFormatter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class MemberFormatterTest {

    private val formatter = MemberFormatter()

    @Test
    fun `can format empty signatures`() {
        assertThat(formatter.format("")).isEqualTo("")
        assertThat(formatter.format(" ")).isEqualTo("")
        assertThat(formatter.format("()")).isEqualTo("")
        assertThat(formatter.format("()V")).isEqualTo("")
        assertThat(formatter.format("()I")).isEqualTo("")
    }

    @Test
    fun `can format signatures with a single parameter of primitive type`() {
        assertThat(formatter.format("(Z)V")).isEqualTo("Boolean")
        assertThat(formatter.format("(B)V")).isEqualTo("Byte")
        assertThat(formatter.format("(C)V")).isEqualTo("Character")
        assertThat(formatter.format("(S)V")).isEqualTo("Short")
        assertThat(formatter.format("(I)V")).isEqualTo("Integer")
        assertThat(formatter.format("(J)V")).isEqualTo("Long")
        assertThat(formatter.format("(F)V")).isEqualTo("Float")
        assertThat(formatter.format("(D)V")).isEqualTo("Double")
    }

    @Test
    fun `can format signatures with a single parameter of non-primitive type`() {
        assertThat(formatter.format("(LString;)V")).isEqualTo("String")
        assertThat(formatter.format("(Ljava/lang/String;)V")).isEqualTo("String")
        assertThat(formatter.format("(Ljava/sql/SqlException;)V")).isEqualTo("SqlException")
    }

    @Test
    fun `can format signatures with a single array parameter`() {
        assertThat(formatter.format("([Z)V")).isEqualTo("Boolean[]")
        assertThat(formatter.format("([B)V")).isEqualTo("Byte[]")
        assertThat(formatter.format("([C)V")).isEqualTo("Character[]")
        assertThat(formatter.format("([S)V")).isEqualTo("Short[]")
        assertThat(formatter.format("([I)V")).isEqualTo("Integer[]")
        assertThat(formatter.format("([J)V")).isEqualTo("Long[]")
        assertThat(formatter.format("([F)V")).isEqualTo("Float[]")
        assertThat(formatter.format("([D)V")).isEqualTo("Double[]")
        assertThat(formatter.format("([[D)V")).isEqualTo("Double[][]")
        assertThat(formatter.format("([LString;)V")).isEqualTo("String[]")
        assertThat(formatter.format("([Ljava/lang/String;)V")).isEqualTo("String[]")
    }

    @Test
    fun `can format signatures with two parameters of primitive types`() {
        assertThat(formatter.format("(ZZ)V")).isEqualTo("Boolean, Boolean")
        assertThat(formatter.format("(BZ)V")).isEqualTo("Byte, Boolean")
        assertThat(formatter.format("(CI)V")).isEqualTo("Character, Integer")
        assertThat(formatter.format("(SF)V")).isEqualTo("Short, Float")
        assertThat(formatter.format("(IJ)V")).isEqualTo("Integer, Long")
    }

    @Test
    fun `can format signatures with multiple array parameters`() {
        assertThat(formatter.format("([Z[Z)V")).isEqualTo("Boolean[], Boolean[]")
        assertThat(formatter.format("([Ljava/lang/String;I)V")).isEqualTo("String[], Integer")
        assertThat(formatter.format("([[Ljava/lang/String;I)V")).isEqualTo("String[][], Integer")
        assertThat(formatter.format("(J[Ljava/lang/String;I)V")).isEqualTo("Long, String[], Integer")
    }

    @Test
    fun `can format signatures with parameters of a mix of primitive and non-primitive types`() {
        assertThat(formatter.format("(ZLjava/lang/String;)V"))
                .isEqualTo("Boolean, String")
        assertThat(formatter.format("(Ljava/lang/String;Z)V"))
                .isEqualTo("String, Boolean")
        assertThat(formatter.format("(Ljava/lang/String;Ljava/math/BigInteger;)V"))
                .isEqualTo("String, BigInteger")
        assertThat(formatter.format("(Ljava/lang/String;IJLjava/math/BigInteger;)V"))
                .isEqualTo("String, Integer, Long, BigInteger")
    }

    @Test
    fun `can format signatures with numerous parameters`() {
        assertThat(formatter.format("(IIIII)V"))
                .isEqualTo("Integer, Integer, Integer, Integer, Integer")
        assertThat(formatter.format("(IZIZI)V"))
                .isEqualTo("Integer, Boolean, Integer, Boolean, Integer")
        assertThat(formatter.format("(SIIIJ)V"))
                .isEqualTo("Short, Integer, Integer, Integer, Long")
    }

}