package net.corda.djvm.utilities

import net.corda.djvm.analysis.SourceLocation
import net.corda.djvm.messages.Message
import net.corda.djvm.messages.MessageCollection

/**
 * Utility for processing a set of entries in a list matching a particular type.
 */
object Processor {

    /**
     * Process entries of type [T] in the provided list, using a guard around the processing of each item, catching
     * any [Message] that might get raised.
     */
    inline fun <reified T> processEntriesOfType(
            list: List<*>,
            messages: MessageCollection,
            processor: (T) -> Unit
    ) {
        for (item in list.filterIsInstance<T>()) {
            try {
                processor(item)
            } catch (exception: Throwable) {
                messages.add(Message.fromThrowable(exception, SourceLocation(item.toString())))
            }
        }
    }

}
