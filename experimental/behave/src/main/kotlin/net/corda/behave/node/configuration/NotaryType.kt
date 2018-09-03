package net.corda.behave.node.configuration

enum class NotaryType {

    NONE,
    VALIDATING,
    NON_VALIDATING
}

fun String.toNotaryType(): NotaryType? {
    return when (this.toLowerCase()) {
        "non-validating" -> NotaryType.NON_VALIDATING
        "nonvalidating" -> NotaryType.NON_VALIDATING
        "validating" -> NotaryType.VALIDATING
        else -> null
    }
}
