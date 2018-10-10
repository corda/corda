package net.corda.djvm.assertions

import net.corda.djvm.TestBase
import net.corda.djvm.code.Instruction
import net.corda.djvm.costing.RuntimeCostSummary
import net.corda.djvm.messages.MessageCollection
import net.corda.djvm.messages.Message
import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.references.ClassHierarchy
import net.corda.djvm.references.Member
import net.corda.djvm.references.ReferenceMap
import net.corda.djvm.rewiring.LoadedClass
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.IterableAssert
import org.assertj.core.api.ListAssert
import org.assertj.core.api.ThrowableAssertAlternative

/**
 * Extensions used for testing.
 */
object AssertionExtensions {

    fun assertThat(loadedClass: LoadedClass) =
            AssertiveClassWithByteCode(loadedClass)

    fun assertThat(costs: RuntimeCostSummary) =
            AssertiveRuntimeCostSummary(costs)

    fun assertThat(messages: MessageCollection) =
            AssertiveMessages(messages)

    fun assertThat(hierarchy: ClassHierarchy) =
            AssertiveClassHierarchy(hierarchy)

    fun assertThat(references: ReferenceMap) =
            AssertiveReferenceMap(references)

    inline fun <reified T> IterableAssert<ClassRepresentation>.hasClass(): IterableAssert<ClassRepresentation> = this
            .`as`("HasClass(${T::class.java.name})")
            .anySatisfy {
                assertThat(it.name).isEqualTo(TestBase.nameOf<T>())
            }

    fun IterableAssert<Member>.hasMember(name: String, signature: String): IterableAssert<Member> = this
            .`as`("HasMember($name:$signature)")
            .anySatisfy {
                assertThat(it.memberName).isEqualTo(name)
                assertThat(it.signature).isEqualTo(signature)
            }

    inline fun <reified TInstruction : Instruction> IterableAssert<Pair<Member, Instruction>>.hasInstruction(
            methodName: String, description: String, noinline predicate: ((TInstruction) -> Unit)? = null
    ): IterableAssert<Pair<Member, Instruction>> = this
            .`as`("Has(${TInstruction::class.java.name} in $methodName(), $description)")
            .anySatisfy {
                assertThat(it.first.memberName).isEqualTo(methodName)
                assertThat(it.second).isInstanceOf(TInstruction::class.java)
                predicate?.invoke(it.second as TInstruction)
            }

    fun <T : Throwable> ThrowableAssertAlternative<T>.withProblem(message: String): ThrowableAssertAlternative<T> = this
            .withStackTraceContaining(message)

    fun ListAssert<Message>.withMessage(message: String): ListAssert<Message> = this
            .`as`("HasMessage($message)")
            .anySatisfy {
                Assertions.assertThat(it.message).contains(message)
            }

}
