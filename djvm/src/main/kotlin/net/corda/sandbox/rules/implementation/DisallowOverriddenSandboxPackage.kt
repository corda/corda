package net.corda.sandbox.rules.implementation

import net.corda.sandbox.references.Class
import net.corda.sandbox.rules.ClassRule
import net.corda.sandbox.validation.RuleContext

/**
 * Disallow loading of classes that have been defined in the 'sandbox' root package.
 */
@Suppress("unused")
class DisallowOverriddenSandboxPackage : ClassRule() {

    override fun validate(context: RuleContext, clazz: Class) = context.validate {
        fail("Cannot load class explicitly defined in the 'sandbox' root package; ${clazz.name}") given
                (!context.pinnedClasses.matches(clazz.name) && clazz.name.startsWith("sandbox/"))
    }

}
