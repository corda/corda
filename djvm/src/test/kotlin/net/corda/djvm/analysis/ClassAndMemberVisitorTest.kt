package net.corda.djvm.analysis

import net.corda.djvm.TestBase
import net.corda.djvm.annotations.NonDeterministic
import net.corda.djvm.assertions.AssertionExtensions.hasClass
import net.corda.djvm.assertions.AssertionExtensions.hasInstruction
import net.corda.djvm.assertions.AssertionExtensions.hasMember
import net.corda.djvm.code.EmitterModule
import net.corda.djvm.code.Instruction
import net.corda.djvm.code.instructions.MemberAccessInstruction
import net.corda.djvm.code.instructions.TryFinallyBlock
import net.corda.djvm.code.instructions.TypeInstruction
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.Member
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

@Suppress("unused")
class ClassAndMemberVisitorTest : TestBase() {

    @Test
    fun `can traverse classes`() {
        val classesVisited = mutableSetOf<ClassRepresentation>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitClass(clazz: ClassRepresentation): ClassRepresentation {
                classesVisited.add(clazz)
                return clazz
            }
        }

        visitor.analyze<TestClass>(context)
        assertThat(classesVisited)
                .hasSize(1)
                .hasClass<TestClass>()

        visitor.analyze<AnotherTestClass>(context)
        assertThat(classesVisited)
                .hasSize(2)
                .hasClass<TestClass>()
                .hasClass<AnotherTestClass>()
    }

    private class TestClass

    private class AnotherTestClass

    @Test
    fun `can traverse fields`() {
        val membersVisited = mutableSetOf<Member>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitField(clazz: ClassRepresentation, field: Member): Member {
                membersVisited.add(field)
                return field
            }
        }
        visitor.analyze<TestClassWithFields>(context)
        assertThat(membersVisited)
                .hasSize(3)
                .hasMember("one", "Z")
                .hasMember("two", "Ljava/lang/String;")
                .hasMember("three", "I")
    }

    private class TestClassWithFields {

        @JvmField
        val one: Boolean = false

        @JvmField
        val two: String = ""

        @JvmField
        val three: Int = 0

    }

    @Test
    fun `can traverse methods`() {
        val membersVisited = mutableSetOf<Member>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitMethod(clazz: ClassRepresentation, method: Member): Member {
                membersVisited.add(method)
                return method
            }
        }
        visitor.analyze<TestClassWithMethods>(context)
        assertThat(membersVisited)
                .hasSize(3)
                .hasMember("<init>", "()V")
                .hasMember("foo", "()Ljava/lang/String;")
                .hasMember("bar", "()I")
    }

    private class TestClassWithMethods {

        fun foo(): String = ""

        fun bar(): Int = 0

    }

    @Test
    fun `can traverse class annotations`() {
        val annotations = mutableSetOf<String>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitClassAnnotation(clazz: ClassRepresentation, descriptor: String) {
                annotations.add(descriptor)
            }
        }
        visitor.analyze<TestClassWithAnnotations>(context)
        assertThat(annotations)
                .hasSize(2)
                .contains("Lkotlin/Metadata;")
                .contains("Lnet/corda/djvm/annotations/NonDeterministic;")
    }

    @NonDeterministic
    private class TestClassWithAnnotations

    @Test
    fun `can traverse member annotations`() {
        val annotations = mutableSetOf<String>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitMemberAnnotation(clazz: ClassRepresentation, member: Member, descriptor: String) {
                annotations.add("${member.memberName}:$descriptor")
            }
        }
        visitor.analyze<TestClassWithMemberAnnotations>(context)
        assertThat(annotations)
                .hasSize(2)
                .contains("field\$annotations:Lnet/corda/djvm/annotations/NonDeterministic;")
                .contains("method:Lnet/corda/djvm/annotations/NonDeterministic;")
    }

    private class TestClassWithMemberAnnotations {

        @NonDeterministic
        val field: Boolean = false

        @NonDeterministic
        fun method() {
        }

    }

    @Test
    fun `can traverse class sources`() {
        val sources = mutableSetOf<String>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitSource(clazz: ClassRepresentation, source: String) {
                sources.add(source)
            }
        }
        visitor.analyze<TestClass>(context)
        val expectedSource = ".*ClassAndMemberVisitorTest.kt"
        assertThat(sources)
                .hasSize(1)
                .`as`("HasSource($expectedSource)")
                .anySatisfy { assertThat(it).containsPattern(expectedSource) }
    }

    @Test
    fun `can traverse instructions`() {
        val instructions = mutableSetOf<Pair<Member, Instruction>>()
        val visitor = object : ClassAndMemberVisitor() {
            override fun visitInstruction(method: Member, emitter: EmitterModule, instruction: Instruction) {
                instructions.add(Pair(method, instruction))
            }
        }
        visitor.analyze<TestClassWithCode>(context)
        assertThat(instructions)
                .isNotEmpty
                .hasInstruction<TypeInstruction>(
                        "foo", "sandbox/java/lang/Object"
                ) {
                    assertThat(it.typeName).isEqualTo("sandbox/java/lang/Object")
                }
                .hasInstruction<MemberAccessInstruction>(
                        "foo", "sandbox/java/lang/Object:<init>:()V"
                ) {
                    assertThat(it.owner).isEqualTo("sandbox/java/lang/Object")
                    assertThat(it.memberName).isEqualTo("<init>")
                    assertThat(it.signature).isEqualTo("()V")
                }
                .hasInstruction<MemberAccessInstruction>(
                        "foo", "sandbox/java/lang/Object:hashCode()I"
                ) {
                    assertThat(it.owner).isEqualTo("sandbox/java/lang/Object")
                    assertThat(it.memberName).isEqualTo("hashCode")
                    assertThat(it.signature).isEqualTo("()I")
                }
                .hasInstruction<TryFinallyBlock>("bar", "TryFinallyBlock")
                .hasInstruction<MemberAccessInstruction>("bar", "MemberAccessInstruction")
    }

    private class TestClassWithCode {

        fun foo(): Int {
            val obj = sandbox.java.lang.Object()
            return obj.hashCode()
        }

        fun bar() {
            synchronized(this) {
                assertThat(1).isEqualTo(1)
            }
        }

    }

}
