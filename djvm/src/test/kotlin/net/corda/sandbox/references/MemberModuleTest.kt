package net.corda.sandbox.references

import net.corda.sandbox.annotations.NonDeterministic
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.annotations.NotNull
import org.junit.Test

class MemberModuleTest {

    private val module = MemberModule()

    @Test
    fun `can detect empty parameter list based on signature`() {
        assertThat(module.numberOfArguments("")).isEqualTo(0)
        assertThat(module.numberOfArguments("()V")).isEqualTo(0)
    }

    @Test
    fun `can detect number of parameters based on trivial signature`() {
        assertThat(module.numberOfArguments("(I)V")).isEqualTo(1)
        assertThat(module.numberOfArguments("(IJ)V")).isEqualTo(2)
    }

    @Test
    fun `can detect number of parameters based on signature containing arrays`() {
        assertThat(module.numberOfArguments("([IJ)V")).isEqualTo(2)
        assertThat(module.numberOfArguments("([[I[J)V")).isEqualTo(2)
        assertThat(module.numberOfArguments("(B[[I[J)V")).isEqualTo(3)
        assertThat(module.numberOfArguments("(B[[I[JI)V")).isEqualTo(4)
    }

    @Test
    fun `can detect number of parameters based on signature containing delegates`() {
        assertThat(module.numberOfArguments("(B[[I[J(I)I)V")).isEqualTo(4)
        assertThat(module.numberOfArguments("(B[[I[J(I)IJ)V")).isEqualTo(5)
    }

    @Test
    fun `can detect number of parameters based on signature with long names`() {
        assertThat(module.numberOfArguments("(ILjava/lang/String;)V")).isEqualTo(2)
        assertThat(module.numberOfArguments("(ILfoo/Bar;JLbar/Foo;)V")).isEqualTo(4)
    }

    @Test
    fun `can detect void returns based on signature`() {
        assertThat(module.returnsValueOrReference("()V")).isEqualTo(false)
        assertThat(module.returnsValueOrReference("(Ljava/lang/String;[IJ)V")).isEqualTo(false)
    }

    @Test
    fun `can detect primitive value returns based on signature`() {
        assertThat(module.returnsValueOrReference("()I")).isEqualTo(true)
        assertThat(module.returnsValueOrReference("(IJ)I")).isEqualTo(true)
        assertThat(module.returnsValueOrReference("([IJ)I")).isEqualTo(true)
        assertThat(module.returnsValueOrReference("(Ljava/lang/String;[IJ)I")).isEqualTo(true)
    }

    @Test
    fun `can detect array value returns based on signature`() {
        assertThat(module.returnsValueOrReference("()[B")).isEqualTo(true)
        assertThat(module.returnsValueOrReference("(IJ)[[Z")).isEqualTo(true)
    }

    @Test
    fun `can detect object returns based on signature`() {
        assertThat(module.returnsValueOrReference("()Ljava/lang/Object;")).isEqualTo(true)
        assertThat(module.returnsValueOrReference("()Lfoo/bar/Baz;")).isEqualTo(true)
    }

    @Test
    fun `can get qualifying identifier`() {
        assertThat(module.getQualifyingIdentifier("foo", "()V")).isEqualTo("foo:()V")
        assertThat(module.getQualifyingIdentifier("bar", "(IJ)Z")).isEqualTo("bar:(IJ)Z")
    }

    @Test
    fun `can detect class references in signatures`() {
        assertThat(module.findReferencedClasses(reference("()V")))
                .containsExactly()
        assertThat(module.findReferencedClasses(reference("(IJ)V")))
                .containsExactly()
        assertThat(module.findReferencedClasses(reference("(Lcom/foo/Bar;)V")))
                .containsExactly("com/foo/Bar")
        assertThat(module.findReferencedClasses(reference("(Lcom/foo/Bar;Lnet/bar/Baz;)V")))
                .containsExactly("com/foo/Bar", "net/bar/Baz")
        assertThat(module.findReferencedClasses(reference("(ILcom/foo/Bar;JLnet/bar/Baz;Z)V")))
                .containsExactly("com/foo/Bar", "net/bar/Baz")
        assertThat(module.findReferencedClasses(reference("(ILcom/foo/Bar;JLnet/bar/Baz;Z)Lnet/a/B;")))
                .containsExactly("com/foo/Bar", "net/bar/Baz", "net/a/B")
    }

    @Test
    fun `can determine if annotation is one for marking class or member non-deterministic`() {
        assertThat(module.isNonDeterministic(NotNull::class.java.descriptor)).isFalse()
        assertThat(module.isNonDeterministic(NonDeterministic::class.java.descriptor)).isTrue()
    }

    @Test
    fun `can detect fields from signatures`() {
        assertThat(module.isField(reference("()V"))).isFalse()
        assertThat(module.isField(reference("(IJ)V"))).isFalse()
        assertThat(module.isField(reference("(IJ)Lfoo/Bar;"))).isFalse()
        assertThat(module.isField(reference("(Ljava/lang/String;J)V"))).isFalse()
        assertThat(module.isField(reference("(Ljava/lang/String;J)Lfoo/Bar;"))).isFalse()
        assertThat(module.isField(reference("V"))).isTrue()
        assertThat(module.isField(reference("[Z"))).isTrue()
        assertThat(module.isField(reference("Ljava/lang/String;"))).isTrue()
        assertThat(module.isField(reference("[Ljava/lang/String;"))).isTrue()
    }

    @Test
    fun `can detect methods from signatures`() {
        assertThat(module.isMethod(reference("()V"))).isTrue()
        assertThat(module.isMethod(reference("(IJ)V"))).isTrue()
        assertThat(module.isMethod(reference("(IJ)Lfoo/Bar;"))).isTrue()
        assertThat(module.isMethod(reference("(Ljava/lang/String;J)V"))).isTrue()
        assertThat(module.isMethod(reference("(Ljava/lang/String;J)Lfoo/Bar;"))).isTrue()
        assertThat(module.isMethod(reference("V"))).isFalse()
        assertThat(module.isMethod(reference("[Z"))).isFalse()
        assertThat(module.isMethod(reference("Ljava/lang/String;"))).isFalse()
        assertThat(module.isMethod(reference("[Ljava/lang/String;"))).isFalse()
    }

    @Test
    fun `can detect constructors from signatures`() {
        assertThat(module.isConstructor(member("foo"))).isFalse()
        assertThat(module.isConstructor(member("helloWorld"))).isFalse()
        assertThat(module.isConstructor(member("init"))).isFalse()
        assertThat(module.isConstructor(member("<INIT>"))).isFalse()
        assertThat(module.isConstructor(member("<init>"))).isTrue()
        assertThat(module.isConstructor(member("<clinit>"))).isTrue()
    }

    private val java.lang.Class<*>.descriptor: String
        get() = "L${name.replace('.', '/')};"

    private fun member(member: String) =
            MemberReference("", member, "")

    private fun reference(signature: String) =
            MemberReference("", "", signature)

}
