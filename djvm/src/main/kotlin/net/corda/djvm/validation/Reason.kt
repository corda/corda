package net.corda.djvm.validation

/**
 * Representation of the reason for why a reference has been marked as invalid.
 *
 * @property code The code used to label the error.
 * @property classes A set of invalid class references, where applicable.
 */
data class Reason(
        val code: Code,
        val classes: List<String> = emptyList()
) {

    /**
     * The derived description of the error.
     */
    val description = classes.joinToString(", ").let {
        when {
            classes.size == 1 -> "${code.singularDescription}; $it"
            classes.size > 1 -> "${code.pluralDescription}; $it"
            else -> code.singularDescription
        }
    }

    /**
     * Error codes used to label invalid references.
     *
     * @property singularDescription The description to use when [classes] is empty or has one element.
     * @property pluralDescription The description to use when [classes] has more than one element.
     */
    @Suppress("KDocMissingDocumentation")
    enum class Code(
            val singularDescription: String,
            val pluralDescription: String = singularDescription
    ) {
        INVALID_CLASS(
                singularDescription = "entity signature contains an invalid reference",
                pluralDescription = "entity signature contains invalid references"
        ),
        NOT_WHITELISTED("entity is not whitelisted"),
        ANNOTATED("entity is annotated with @NonDeterministic"),
        NON_EXISTENT_CLASS("class does not exist"),
        NON_EXISTENT_MEMBER("member does not exist")
    }

}