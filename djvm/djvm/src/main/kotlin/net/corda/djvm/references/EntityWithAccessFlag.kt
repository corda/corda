package net.corda.djvm.references

/**
 * An entity in a class hierarchy that is attributed zero or more access flags.
 *
 * @property access The access flags of the class.
 */
interface EntityWithAccessFlag {
    val access: Int
}
