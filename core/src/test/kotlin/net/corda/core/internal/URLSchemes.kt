package net.corda.core.internal

import net.corda.core.serialization.internal.AttachmentURLStreamHandlerFactory
import java.net.URL

/**
 * A JVM can only ever invoke [URL.setURLStreamHandlerFactory] once.
 */
object URLSchemes {
    init {
        URL.setURLStreamHandlerFactory(AttachmentURLStreamHandlerFactory)
    }
}
