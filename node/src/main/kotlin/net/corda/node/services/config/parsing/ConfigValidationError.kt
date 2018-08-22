package net.corda.node.services.config.parsing

// TODO sollecitom here
class ConfigValidationError(val keyName: String, val typeName: String, val message: String, val containingPath: String? = null) {

    val path: String = containingPath?.let { parent -> "$parent.$keyName" } ?: keyName
}