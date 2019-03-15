package net.corda.djvm.assertions

import net.corda.djvm.references.ClassReference
import net.corda.djvm.references.MemberReference
import net.corda.djvm.references.ReferenceMap
import org.assertj.core.api.Assertions.*

@Suppress("unused")
open class AssertiveReferenceMap(private val references: ReferenceMap) {

    fun hasCount(count: Int): AssertiveReferenceMap {
        val allReferences = references.joinToString("\n") { " - $it" }
        assertThat(references.numberOfReferences)
                .overridingErrorMessage("Expected $count reference(s), found:\n$allReferences")
                .isEqualTo(count)
        return this
    }

    fun hasClass(clazz: String): AssertiveReferenceMapWithEntity {
        assertThat(references)
                .`as`("Class($clazz)")
                .anySatisfy {
                    assertThat(it).isInstanceOf(ClassReference::class.java)
                    if (it is ClassReference) {
                        assertThat(it.className).isEqualTo(clazz)
                    }
                }
        val reference = ClassReference(clazz)
        return AssertiveReferenceMapWithEntity(references, reference, references.locationsFromReference(reference))
    }

    fun hasMember(owner: String, member: String, signature: String): AssertiveReferenceMapWithEntity {
        assertThat(references)
                .`as`("Member($owner.$member)")
                .anySatisfy {
                    assertThat(it).isInstanceOf(MemberReference::class.java)
                    if (it is MemberReference) {
                        assertThat(it.className).isEqualTo(owner)
                        assertThat(it.memberName).isEqualTo(member)
                    }
                }
        val reference = MemberReference(owner, member, signature)
        return AssertiveReferenceMapWithEntity(references, reference, references.locationsFromReference(reference))
    }

}
