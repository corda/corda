package net.corda.core.flows

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Stack

data class StackDump(
        val stackFrames: List<Frame>,
        val stackWithoutFrames: List<Any?>
) {
    data class Frame(
            val stackTraceElement: StackTraceElement?, // This should be the call that *pushed* the frame of [objects]
            val objects: List<Any?>
    )

    companion object {
        fun extractFromFiber(fiber: Fiber<*>, stackTrace: List<StackTraceElement>): StackDump {
            val stack = getFiberStack(fiber)
            val objectStack = getObjectStack(stack).toList()
            val frameOffsets = getFrameOffsets(stack)
            val frameObjects = frameOffsets.map { (frameOffset, frameSize) ->
                objectStack.subList(frameOffset + 1, frameOffset + frameSize + 1)
            }
            val relevantStackTrace = removeFirstStackTraceElements(removeConstructorStackTraceElements(stackTrace))
            // This is a heuristic pairing of the frames with stack frame elements, if they don't pair up add a ??? next to the method name
            val warningPrefix = if (relevantStackTrace.size != frameObjects.size) {
                "??? "
            } else {
                ""
            }
            val reformattedStackTrace = relevantStackTrace.map {
                StackTraceElement("$warningPrefix${it.className}", it.methodName, it.fileName, it.lineNumber)
            }
            val frames = frameObjects.asReversed().mapIndexed { index, objects ->
                Frame(reformattedStackTrace.getOrNull(index), objects)
            }
            return StackDump(frames, objectStack.toList())
        }

        private fun removeFirstStackTraceElements(stackTrace: List<StackTraceElement>): List<StackTraceElement> {
            if (stackTrace.isEmpty()) {
                return stackTrace
            } else {
                return stackTrace.subList(1, stackTrace.size)
            }
        }

        private fun removeConstructorStackTraceElements(stackTrace: List<StackTraceElement>): List<StackTraceElement> {
            val newStackTrace = ArrayList<StackTraceElement>()
            var previousElement: StackTraceElement? = null
            for (element in stackTrace) {
                if (element.methodName == previousElement?.methodName &&
                        element.className == previousElement?.className &&
                        element.fileName == previousElement?.fileName) {
                    continue
                }
                newStackTrace.add(element)
                previousElement = element
            }
            return newStackTrace
        }
    }
}

/**
 * This is a workaround for https://github.com/puniverse/quasar/issues/282 by traversing the stack from the bottom,
 * nulling out all non-live references
 */
fun workaround_Github_puniverse_quasar_282(fiber: Fiber<*>) {
    val stack = getFiberStack(fiber)
    val objects = getObjectStack(stack)
    val offsets = getFrameOffsets(stack)
    for ((offset) in offsets) {
        objects[offset] = null
    }
    val (lastOffset, lastSlots) = offsets.lastOrNull() ?: 0 to 0
    for (i in lastOffset + lastSlots + 1 .. objects.size - 1) {
        objects[i] = null
    }
}

private inline fun <reified R, A> R.getField(name: String): A {
    val field = R::class.java.getDeclaredField(name)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return field.get(this) as A
}

private fun getFiberStack(fiber: Fiber<*>): Stack {
    return fiber.getField("stack")
}

private fun getObjectStack(stack: Stack): Array<Any?> {
    return stack.getField("dataObject")
}

private fun getPrimitiveStack(stack: Stack): LongArray {
    return stack.getField("dataLong")
}

// returns pairs of (offset, size of frame)
private fun getFrameOffsets(stack: Stack): List<Pair<Int, Int>> {
    val primitiveStack = getPrimitiveStack(stack)
    val offsets = ArrayList<Pair<Int, Int>>()
    var offset = 0
    while (true) {
        val record = primitiveStack[offset]
        val slots = getNumSlots(record)
        if (slots > 0) {
            offsets.add(offset to slots)
            offset += slots + 1
        } else {
            break
        }
    }
    return offsets
}

private val MASK_FULL: Long = -1L

private fun getNumSlots(record: Long): Int {
    return getUnsignedBits(record, 14, 16).toInt()
}

private fun getUnsignedBits(word: Long, offset: Int, length: Int): Long {
    val a = 64 - length
    val b = a - offset
    return word.ushr(b) and MASK_FULL.ushr(a)
}
