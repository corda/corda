package net.corda.djvm.rules.implementation

import net.corda.djvm.references.ClassRepresentation
import net.corda.djvm.rules.ClassRule
import net.corda.djvm.validation.RuleContext

/**
 * Disallow loading of classes that have been defined in the 'sandbox' root package.
 */
@Suppress("unused")
class DisallowOverriddenSandboxPackage : ClassRule() {

    override fun validate(context: RuleContext, clazz: ClassRepresentation) = context.validate {
        fail("Cannot load class explicitly defined in the 'sandbox' root package; ${clazz.name}") given
                (clazz.name !in context.pinnedClasses && clazz.name.startsWith("sandbox/"))
    }

}
