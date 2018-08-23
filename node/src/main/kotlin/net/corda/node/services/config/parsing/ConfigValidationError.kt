package net.corda.node.services.config.parsing

class ConfigValidationError(val keyName: String, val typeName: String? = null, val message: String, val containingPath: String? = null) {

    val path: String = containingPath?.let { parent -> "$parent.$keyName" } ?: keyName

    fun withContainingPath(containingPath: String?) = ConfigValidationError(keyName, typeName, message, containingPath)

    override fun toString(): String {

        return "(keyName='$keyName', typeName='$typeName', containingPath=$containingPath, path=$path, message='$message')"
    }
}