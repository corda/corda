package net.corda.serialization.internal.model

/**
 * An exception to which information can be appended.
 */
interface AppendableException {
    fun append(strToAppend: String)
}

/**
 * Utility function which helps tracking the path in the object graph when exceptions are thrown.
 * Since there might be a chain of nested calls it is useful to record which part of the graph caused an issue.
 * Path information is added to the message of the exception being thrown.
 */
internal inline fun <T> ifThrowsAppend(strToAppendFn: () -> String, block: () -> T): T {
    try {
        return block()
    } catch (th: Throwable) {
        if (th is AppendableException) {
            th.append(strToAppendFn())
        } else {
            th.setMessage("${strToAppendFn()} -> ${th.message}")
        }
        throw th
    }
}

/**
 * Not a public property so will have to use reflection
 */
private fun Throwable.setMessage(newMsg: String) {
    val detailMessageField = Throwable::class.java.getDeclaredField("detailMessage")
    detailMessageField.isAccessible = true
    detailMessageField.set(this, newMsg)
}