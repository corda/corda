package net.corda.sandbox.assertions

import net.corda.sandbox.TestBase
import net.corda.sandbox.code.Instruction
import net.corda.sandbox.costing.RuntimeCostSummary
import net.corda.sandbox.messages.MessageCollection
import net.corda.sandbox.messages.Message
import net.corda.sandbox.references.Class
import net.corda.sandbox.references.ClassHierarchy
import net.corda.sandbox.references.Member
import net.corda.sandbox.references.ReferenceMap
import net.corda.sandbox.rewiring.LoadedClass
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

    inline fun <reified T> IterableAssert<Class>.hasClass(): IterableAssert<Class> = this
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
