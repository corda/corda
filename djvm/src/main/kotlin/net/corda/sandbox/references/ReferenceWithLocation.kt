package net.corda.sandbox.references

import net.corda.sandbox.analysis.SourceLocation

/**
 * Representation of a reference with its original source location.
 *
 * @property location The source location from which the reference was made.
 * @property reference The class or class member that was being referenced.
 * @property description An optional description of the reference itself or the reason for why the reference was
 * created.
 */
data class ReferenceWithLocation(
        val location: SourceLocation,
        val reference: EntityReference,
        val description: String = ""
)