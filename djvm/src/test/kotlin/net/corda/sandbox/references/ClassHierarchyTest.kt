package net.corda.sandbox.references

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.objectweb.asm.Opcodes

class ClassHierarchyTest {

    private val classModule = ClassModule()

    private val memberModule = MemberModule()

    private val classes = ClassHierarchy()

    @Test
    fun `can register class in hierarchy`() {
        val clazz = clazz<Foo>()
        classes.add(clazz)
        assertThat(classes.contains(clazz.name)).isTrue()
    }

    @Test
    fun `can register multiple classes in hierarchy`() {
        val clazz1 = clazz<Foo>()
        val clazz2 = clazz<Bar>()
        classes.add(clazz1)
        classes.add(clazz2)
        assertThat(classes.names).contains(clazz1.name, clazz2.name)
    }

    private open class Foo

    private class Bar : Foo()

    @Test
    fun `can derive members from classes in hierarchy`() {
        val clazz1 = clazz<FirstClass>()
                .withMember("method", "()V")
        classes.add(clazz1)
        val member = classes.getMember(clazz<FirstClass>().name, "method", "()V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<FirstClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "()V")
    }

    @Test
    fun `cannot derive non-existent members from classes in hierarchy`() {
        val clazz1 = clazz<FirstClass>()
                .withMember("method", "()V")
        classes.add(clazz1)
        val member = classes.getMember(clazz<FirstClass>().name, "nonExistent", "()V")
        assertThat(member).isNull()
    }

    @Test
    fun `can derive inherited members from classes in hierarchy`() {
        val clazz1 = clazz<FirstClass>()
                .withMember("method", "()V")
        val clazz2 = clazzWithSuper<SecondClass, FirstClass>()
        classes.add(clazz1)
        classes.add(clazz2)
        val member = classes.getMember(clazz<SecondClass>().name, "method", "()V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<FirstClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "()V")
    }

    @Test
    fun `can derive members from classes with ancestors in hierarchy`() {
        val clazz1 = clazz<FirstClass>()
        val clazz2 = clazzWithSuper<SecondClass, FirstClass>()
                .withMember("method", "()V")
        classes.add(clazz1)
        classes.add(clazz2)
        val member = classes.getMember(clazz<SecondClass>().name, "method", "()V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<SecondClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "()V")
    }

    @Test
    fun `can derive inherited members from classes in hierarchy when multiple levels deep`() {
        val clazz1 = clazz<FirstClass>()
                .withMember("method", "()V")
        val clazz2 = clazzWithSuper<SecondClass, FirstClass>()
                .withMember("method", "()V")
        val clazz3 = clazzWithSuper<ThirdClass, SecondClass>()
        val clazz4 = clazzWithSuper<FourthClass, ThirdClass>()
        classes.add(clazz1)
        classes.add(clazz2)
        classes.add(clazz3)
        classes.add(clazz4)
        val member = classes.getMember(clazz<FourthClass>().name, "method", "()V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<SecondClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "()V")
    }

    @Test
    fun `can derive the correct inherited member from classes in hierarchy when multiple variants of member exists`() {
        val clazz1 = clazz<FirstClass>()
                .withMember("method", "()V")
                .withMember("method", "(I)V")
        val clazz2 = clazzWithSuper<SecondClass, FirstClass>()
                .withMember("method", "(J)V")
                .withMember("anotherMethod", "([B)V")
        val clazz3 = clazzWithSuper<ThirdClass, SecondClass>()
                .withMember("method", "(B)V")
        val clazz4 = clazzWithSuper<FourthClass, ThirdClass>()
                .withMember("anotherMethod", "([B)V")
        classes.add(clazz1)
        classes.add(clazz2)
        classes.add(clazz3)
        classes.add(clazz4)

        var member = classes.getMember(clazz<FourthClass>().name, "method", "()V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<FirstClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "()V")

        member = classes.getMember(clazz<FourthClass>().name, "method", "(I)V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<FirstClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "(I)V")

        member = classes.getMember(clazz<FourthClass>().name, "method", "(J)V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<SecondClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "(J)V")

        member = classes.getMember(clazz<FourthClass>().name, "method", "(B)V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<ThirdClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "method")
                .hasFieldOrPropertyWithValue("signature", "(B)V")

        member = classes.getMember(clazz<FourthClass>().name, "anotherMethod", "([B)V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<FourthClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "anotherMethod")
                .hasFieldOrPropertyWithValue("signature", "([B)V")

        member = classes.getMember(clazz<ThirdClass>().name, "anotherMethod", "([B)V")
        assertThat(member)
                .isNotNull()
                .hasFieldOrPropertyWithValue("className", clazz<SecondClass>().name)
                .hasFieldOrPropertyWithValue("memberName", "anotherMethod")
                .hasFieldOrPropertyWithValue("signature", "([B)V")
    }

    private open class FirstClass

    private open class SecondClass : FirstClass()

    private open class ThirdClass : SecondClass()

    private class FourthClass : ThirdClass()

    private inline fun <reified T> clazz() =
            T::class.java.let {
                val className = classModule.getBinaryClassName(it.name)
                ClassRepresentation(0, 0, className, sourceFile = "${it.simpleName}.kt")
            }

    private inline fun <reified T, reified TSuper> clazzWithSuper() =
            T::class.java.let {
                val apiVersion = Opcodes.V1_8
                val access = 0
                val className = classModule.getBinaryClassName(it.name)
                val superClassName = TSuper::class.java.let {
                    classModule.getBinaryClassName(it.name)
                }
                ClassRepresentation(apiVersion, access, className, superClassName, sourceFile = "${it.simpleName}.kt")
            }

    private fun ClassRepresentation.withMember(memberName: String, signature: String, generics: String = "") = this.apply {
        memberModule.addToClass(this, Member(0, this.name, memberName, signature, generics))
    }

}