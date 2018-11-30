package net.corda.serialization.internal.model

import net.corda.serialization.internal.amqp.TransformTypes
import net.corda.serialization.internal.amqp.TransformsMap

class InvalidEnumTransformsException(message: String): Exception(message)

data class EnumTransforms(
        val defaults: Map<String, String>,
        val renames: Map<String, String>,
        val source: TransformsMap) {

    val size: Int get() = defaults.size + renames.size

    companion object {
        val empty = EnumTransforms(emptyMap(), emptyMap(), TransformsMap(TransformTypes::class.java))
    }

    fun validate(constants: Map<String, Int>) {
        validateNoCycles()

        val constantsBeforeRenaming = constants.asSequence().mapNotNull { (name, index) ->
            renames[name]?.let { newName -> newName to index }
        }.toMap()
        validateDefaults(constants + constantsBeforeRenaming)
    }

    fun validateNoCycles() {
        val unchecked = renames.toMutableMap()
        while (!unchecked.isEmpty()) {
            val terminals = unchecked.asSequence().filterNot { (_, to) -> to in unchecked.keys }.map { (from, _) -> from }.toList()
            if (terminals.isEmpty()) throw InvalidEnumTransformsException("Rename cycles detected in rename map: ${unchecked.keys}")
            terminals.forEach { unchecked.remove(it) }
        }
    }

    private fun validateDefaults(constantsBeforeRenaming: Map<String, Int>) {
        defaults.forEach { (new, old) ->
            requireThat(constantsBeforeRenaming.contains(new)) { "Unknown enum constant $new" }
            requireThat(constantsBeforeRenaming.contains(old)) {
                "Enum extension defaults must be to a valid constant: $new -> $old. $old " +
                        "doesn't exist in constant set $constantsBeforeRenaming"
            }
            requireThat(old != new) { "Enum extension $new cannot default to itself" }
            requireThat(constantsBeforeRenaming[old]!! < constantsBeforeRenaming[new]!!) {
                "Enum extensions must default to older constants. $new[${constantsBeforeRenaming[new]}] " +
                        "defaults to $old[${constantsBeforeRenaming[old]}] which is greater"
            }
        }
    }

    private inline fun requireThat(expr: Boolean, errorMessage: () -> String) {
        if (!expr) {
            throw InvalidEnumTransformsException(errorMessage())
        }
    }
}