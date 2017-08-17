package net.corda.core.flows

import java.time.Instant

/**
 * Main data object representing snapshot of the flow stack, extracted from the Quasar stack.
 */
data class FlowStackSnapshot(
        val time: Instant,
        val flowClass: String,
        val stackFrames: List<Frame>
) {
    data class Frame(
            val stackTraceElement: StackTraceElement, // This should be the call that *pushed* the frame of [objects]
            val stackObjects: List<Any?>
    ) {
        override fun toString(): String = stackTraceElement.toString()
    }
}

/**
 * Token class, used to indicate stack presence of the corda internal data. Since this data is of no use for
 * a CordApp developer, it is skipped from serialisation and its presence is only marked by this token.
 */
data class StackFrameDataToken(val className: String)