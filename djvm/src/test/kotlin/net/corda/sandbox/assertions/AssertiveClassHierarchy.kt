package net.corda.sandbox.assertions

import net.corda.sandbox.references.ClassHierarchy
import org.assertj.core.api.Assertions

open class AssertiveClassHierarchy(protected val hierarchy: ClassHierarchy) {

    fun hasCount(count: Int): AssertiveClassHierarchy {
        Assertions.assertThat(hierarchy.names.size)
                .`as`("Number of classes")
                .isEqualTo(count)
        return this
    }

    fun hasClass(name: String): AssertiveClassHierarchyWithClass {
        Assertions.assertThat(hierarchy.names)
                .`as`("Class($name)")
                .anySatisfy {
                    Assertions.assertThat(it).isEqualTo(name)
                }
        return AssertiveClassHierarchyWithClass(hierarchy, name)
    }

}
