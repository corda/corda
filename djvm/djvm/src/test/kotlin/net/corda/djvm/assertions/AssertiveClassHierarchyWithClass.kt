package net.corda.djvm.assertions

import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.ClassHierarchy
import org.assertj.core.api.Assertions.*

open class AssertiveClassHierarchyWithClass(
        hierarchy: ClassHierarchy,
        private val className: String
) : AssertiveClassHierarchy(hierarchy) {

    private val clazz: ClassRepresentation
        get() = hierarchy[className]!!

    fun withInterfaceCount(count: Int): AssertiveClassHierarchyWithClass {
        assertThat(clazz.interfaces.size)
                .`as`("$clazz.InterfaceCount($count)")
                .isEqualTo(count)
        return this
    }

    fun withInterface(name: String): AssertiveClassHierarchyWithClass {
        assertThat(clazz.interfaces).contains(name)
        return this
    }

    fun withMemberCount(count: Int): AssertiveClassHierarchyWithClass {
        assertThat(clazz.members.size)
                .`as`("MemberCount($className)")
                .isEqualTo(count)
        return this
    }

    fun withMember(name: String, signature: String): AssertiveClassHierarchyWithClassAndMember {
        assertThat(clazz.members.values)
                .`as`("Member($className.$name:$signature")
                .anySatisfy {
                    assertThat(it.memberName).isEqualTo(name)
                    assertThat(it.signature).isEqualTo(signature)
                }
        val member = clazz.members.values.first {
            it.memberName == name && it.signature == signature
        }
        return AssertiveClassHierarchyWithClassAndMember(hierarchy, className, member)
    }

}
