package net.corda.djvm.assertions

import net.corda.djvm.references.ClassReference
import net.corda.djvm.references.MemberReference
import net.corda.djvm.references.ReferenceMap
import org.assertj.core.api.Assertions

@Suppress("unused")
open class AssertiveReferenceMap(private val references: ReferenceMap) {

    fun hasCount(count: Int): AssertiveReferenceMap {
        val allReferences = references.joinToString("\n") { " - $it" }
        Assertions.assertThat(references.size)
                .overridingErrorMessage("Expected $count reference(s), found:\n$allReferences")
                .isEqualTo(count)
        return this
    }

    fun hasClass(clazz: String): AssertiveReferenceMapWithEntity {
        Assertions.assertThat(references.iterator())
                .`as`("Class($clazz)")
                .anySatisfy {
                    Assertions.assertThat(it).isInstanceOf(ClassReference::class.java)
                    if (it is ClassReference) {
                        Assertions.assertThat(it.className).isEqualTo(clazz)
                    }
                }
        val reference = ClassReference(clazz)
        return AssertiveReferenceMapWithEntity(references, reference, references.locationsFromReference(reference))
    }

    fun hasMember(owner: String, member: String, signature: String): AssertiveReferenceMapWithEntity {
        Assertions.assertThat(references.iterator())
                .`as`("Member($owner.$member)")
                .anySatisfy {
                    Assertions.assertThat(it).isInstanceOf(MemberReference::class.java)
                    if (it is MemberReference) {
                        Assertions.assertThat(it.className).isEqualTo(owner)
                        Assertions.assertThat(it.memberName).isEqualTo(member)
                    }
                }
        val reference = MemberReference(owner, member, signature)
        return AssertiveReferenceMapWithEntity(references, reference, references.locationsFromReference(reference))
    }

}
