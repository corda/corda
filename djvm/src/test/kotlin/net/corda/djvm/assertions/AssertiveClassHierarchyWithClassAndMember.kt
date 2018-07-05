package net.corda.djvm.assertions

import net.corda.djvm.references.ClassHierarchy
import net.corda.djvm.references.Member
import org.assertj.core.api.Assertions

@Suppress("unused", "CanBeParameter")
class AssertiveClassHierarchyWithClassAndMember(
        hierarchy: ClassHierarchy,
        private val className: String,
        private val member: Member
) : AssertiveClassHierarchyWithClass(hierarchy, className) {

    fun withAccessFlag(flag: Int): AssertiveClassHierarchyWithClassAndMember {
        Assertions.assertThat(member.access and flag)
                .`as`("$member.AccessFlag($flag)")
                .isNotEqualTo(0)
        return this
    }

    fun withNoAccessFlag(flag: Int): AssertiveClassHierarchyWithClassAndMember {
        Assertions.assertThat(member.access and flag)
                .`as`("$member.AccessFlag($flag)")
                .isEqualTo(0)
        return this
    }

}
