package net.corda.djvm.assertions

import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.ClassHierarchy
import org.assertj.core.api.Assertions

open class AssertiveClassHierarchyWithClass(
        hierarchy: ClassHierarchy,
        private val className: String
) : AssertiveClassHierarchy(hierarchy) {

    private val clazz: ClassRepresentation
        get() = hierarchy[className]!!

    fun withInterfaceCount(count: Int): AssertiveClassHierarchyWithClass {
        Assertions.assertThat(clazz.interfaces.size)
                .`as`("$clazz.InterfaceCount($count)")
                .isEqualTo(count)
        return this
    }

    fun withInterface(name: String): AssertiveClassHierarchyWithClass {
        Assertions.assertThat(clazz.interfaces).contains(name)
        return this
    }

    fun withMemberCount(count: Int): AssertiveClassHierarchyWithClass {
        Assertions.assertThat(clazz.members.size)
                .`as`("MemberCount($className)")
                .isEqualTo(count)
        return this
    }

    fun withMember(name: String, signature: String): AssertiveClassHierarchyWithClassAndMember {
        Assertions.assertThat(clazz.members.values)
                .`as`("Member($className.$name:$signature")
                .anySatisfy {
                    Assertions.assertThat(it.memberName).isEqualTo(name)
                    Assertions.assertThat(it.signature).isEqualTo(signature)
                }
        val member = clazz.members.values.first {
            it.memberName == name && it.signature == signature
        }
        return AssertiveClassHierarchyWithClassAndMember(hierarchy, className, member)
    }

}
