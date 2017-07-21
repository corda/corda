package net.corda.core.flows

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Instrumented
import co.paralleluniverse.fibers.Stack
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import net.corda.core.internal.FlowStateMachine
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.full.isSubclassOf

data class FlowStackSnapshot internal constructor (
        val timestamp: Long,
        val flowClass: Class<*>?,
        val stackFrames: List<Frame>
) {

    constructor(flowClass: Class<*>, stackFrames: List<Frame>) : this(System.currentTimeMillis(), flowClass, stackFrames)

    //Required by Jackson
    constructor() : this(System.currentTimeMillis(), null, listOf())

    data class Frame(
            val stackTraceElement: StackTraceElement?, // This should be the call that *pushed* the frame of [objects]
            val stackObjects: List<Any?>
    ) {
        //Required by Jackson
        constructor() : this(null, listOf())
    }

    companion object {
        fun extractFromFiber(fiber: Fiber<*>, stackTrace: List<StackTraceElement>, flowClass: Class<*>): FlowStackSnapshot {
            val stack = getFiberStack(fiber)
            val objectStack = getObjectStack(stack).toList()
            val frameOffsets = getFrameOffsets(stack)
            val frameObjects = frameOffsets.map { (frameOffset, frameSize) ->
                objectStack.subList(frameOffset + 1, frameOffset + frameSize + 1)
            }
            val relevantStackTrace = removeFirstStackTraceElements(removeConstructorStackTraceElements(stackTrace))
            val stackTraceToAnnotation = relevantStackTrace.map {
                val element = StackTraceElement(it.className, it.methodName, it.fileName, it.lineNumber)
                element to getInstrumentedAnnotation(element)
            }

            val frameObjectsIterator = frameObjects.listIterator()
            val frames = stackTraceToAnnotation.reversed().map { (element, annotation) ->
                /* If annotation is null the case indicates that this is an entry point - i.e.
                * the net.corda.node.services.statemachine.FlowStateMachineImpl.run method*/
                if (frameObjectsIterator.hasNext() && (annotation == null || !annotation.methodOptimized)) {
                    Frame(element, frameObjectsIterator.next())
                } else {
                    Frame(element, listOf())
                }
            }
            return FlowStackSnapshot(flowClass, frames)
        }

        /* Stores flow stack snapshot as a json file. The stored shapshot is only partial and consists
         * only data (i.e. stack traces and local variables values) relevant to the flow. It does not
         * persist corda internal data (e.g. FlowStateMachine).
         * If the path parameter is not specified then the file is stored in the following path:
         * {CURRENT_DIRECTORY}/flowStackSnapshots/YYYY-MM-DD/flowStackSnapshot-{i}.json,
         * where i is the next unused integer*/
        fun persistAsJsonFile(flowStackSnapshot: FlowStackSnapshot, path:String? = null):Unit {
            val mapper = ObjectMapper()
            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            mapper.enable(SerializationFeature.INDENT_OUTPUT)
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.registerModule(KotlinModule())
            val file = createFile(path)
            file.bufferedWriter().use { out ->
                mapper.writeValue(out, filterOutStackDump(flowStackSnapshot))
            }
        }

        private fun filterOutStackDump(flowStackSnapshot: FlowStackSnapshot):FlowStackSnapshot {
            val framesFilteredByStackTraceElement = flowStackSnapshot.stackFrames.filter {
                !Class.forName(it.stackTraceElement!!.className).kotlin.isSubclassOf(FlowStateMachine::class)
            }
            val framesFilteredByObjects = framesFilteredByStackTraceElement.map {
                Frame(it.stackTraceElement, it.stackObjects.filter {
                    it != null && !(it is FlowLogic<*> || it is FlowStateMachine<*> || it is Fiber<*>)
                })
            }
            return FlowStackSnapshot(flowStackSnapshot.timestamp, flowStackSnapshot.flowClass, framesFilteredByObjects)
        }

        private fun createFile(path: String?):File {
            var file: File
            if (path.isNullOrEmpty()) {
                val dir = File("flowStackSnapshots/${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}/")
                dir.mkdirs()
                var index = 0
                do {
                    file = File(dir, "flowStackSnapshot-${index++}.json")
                } while (file.exists())
            } else {
                file = File(path)
                file.parentFile.mkdirs()
            }
            return file
        }

        private fun getInstrumentedAnnotation(element: StackTraceElement):Instrumented? {
            for (method in Class.forName(element.className).methods) {
                if (method.name == element.methodName && method.isAnnotationPresent(Instrumented::class.java)) {
                    return method.getAnnotation(Instrumented::class.java)
                }
            }
            return null
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
