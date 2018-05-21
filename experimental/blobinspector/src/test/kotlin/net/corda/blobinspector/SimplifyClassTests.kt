package net.corda.blobinspector

import org.junit.Test

class SimplifyClassTests {

    @Test
    fun test1() {
        data class A(val a: Int)

        println(A::class.java.name)
        println(A::class.java.name.simplifyClass())
    }

    @Test
    fun test2() {
        val p = this.javaClass.`package`.name

        println("$p.Class1<$p.Class2>")
        println("$p.Class1<$p.Class2>".simplifyClass())
        println("$p.Class1<$p.Class2, $p.Class3>")
        println("$p.Class1<$p.Class2, $p.Class3>".simplifyClass())
        println("$p.Class1<$p.Class2<$p.Class3>>")
        println("$p.Class1<$p.Class2<$p.Class3>>".simplifyClass())
        println("$p.Class1<$p.Class2<$p.Class3>>")
        println("$p.Class1\$C<$p.Class2<$p.Class3>>".simplifyClass())
    }
}