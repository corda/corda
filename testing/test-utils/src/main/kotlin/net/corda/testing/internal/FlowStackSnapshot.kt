package net.corda.testing.internal

import co.paralleluniverse.fibers.Fiber
import co.paralleluniverse.fibers.Instrumented
import co.paralleluniverse.fibers.Stack
import co.paralleluniverse.fibers.Suspendable
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStackSnapshot
import net.corda.core.flows.FlowStackSnapshot.Frame
import net.corda.core.flows.StackFrameDataToken
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.div
import net.corda.core.internal.write
import net.corda.core.serialization.SerializeAsToken
import net.corda.client.jackson.JacksonSupport
import net.corda.core.internal.uncheckedCast
import net.corda.node.services.statemachine.FlowStackSnapshotFactory
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate

class FlowStackSnapshotFactoryImpl : FlowStackSnapshotFactory {
    @Suspendable
    override fun getFlowStackSnapshot(flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot {
        var snapshot: FlowStackSnapshot? = null
        val stackTrace = Fiber.currentFiber().stackTrace
        Fiber.parkAndSerialize { fiber, _ ->
            snapshot = extractStackSnapshotFromFiber(fiber, stackTrace.toList(), flowClass)
            Fiber.unparkDeserialized(fiber, fiber.scheduler)
        }
        // This is because the dump itself is on the stack, which means it creates a loop in the object graph, we set
        // it to null to break the loop
        val temporarySnapshot = snapshot
        snapshot = null
        return temporarySnapshot!!
    }

    override fun persistAsJsonFile(flowClass: Class<out FlowLogic<*>>, baseDir: Path, flowId: StateMachineRunId) {
        val flowStackSnapshot = getFlowStackSnapshot(flowClass)
        val mapper = JacksonSupport.createNonRpcMapper().apply {
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }
        val file = createFile(baseDir, flowId)
        file.write(createDirs = true) {
            mapper.writeValue(it, filterOutStackDump(flowStackSnapshot))
        }
    }

    private fun extractStackSnapshotFromFiber(fiber: Fiber<*>, stackTrace: List<StackTraceElement>, flowClass: Class<out FlowLogic<*>>): FlowStackSnapshot {
        val stack = getFiberStack(fiber)
        val objectStack = getObjectStack(stack).toList()
        val frameOffsets = getFrameOffsets(stack)
        val frameObjects = frameOffsets.map { (frameOffset, frameSize) ->
            // We need to convert the sublist to a list due to the Kryo lack of support when serializing
            objectStack.subList(frameOffset + 1, frameOffset + frameSize + 1).toList()
        }
        // We drop the first element as it is corda internal call irrelevant from the perspective of a CordApp developer
        val relevantStackTrace = removeConstructorStackTraceElements(stackTrace).drop(1)
        val stackTraceToAnnotation = relevantStackTrace.map {
            val element = StackTraceElement(it.className, it.methodName, it.fileName, it.lineNumber)
            element to element.instrumentedAnnotation
        }
        val frameObjectsIterator = frameObjects.listIterator()
        val frames = stackTraceToAnnotation.reversed().map { (element, annotation) ->
            // If annotation is null then the case indicates that this is an entry point - i.e.
            // the net.corda.node.services.statemachine.FlowStateMachineImpl.run method
            val stackObjects = if (frameObjectsIterator.hasNext() && (annotation == null || !annotation.methodOptimized)) {
                frameObjectsIterator.next()
            } else {
                emptyList()
            }
            Frame(element, stackObjects)
        }
        return FlowStackSnapshot(Instant.now(), flowClass.name, frames)
    }

    private val StackTraceElement.instrumentedAnnotation: Instrumented?
        get() {
            Class.forName(className).methods.forEach {
                if (it.name == methodName && it.isAnnotationPresent(Instrumented::class.java)) {
                    return it.getAnnotation(Instrumented::class.java)
                }
            }
            return null
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

    private fun filterOutStackDump(flowStackSnapshot: FlowStackSnapshot): FlowStackSnapshot {
        val framesFilteredByStackTraceElement = flowStackSnapshot.stackFrames.filter {
            !FlowStateMachine::class.java.isAssignableFrom(Class.forName(it.stackTraceElement.className))
        }
        val framesFilteredByObjects = framesFilteredByStackTraceElement.map {
            it.copy(stackObjects = it.stackObjects.map {
                if (it != null && (it is FlowLogic<*> || it is FlowStateMachine<*> || it is Fiber<*> || it is SerializeAsToken)) {
                    StackFrameDataToken(it::class.java.name)
                } else {
                    it
                }
            })
        }
        return flowStackSnapshot.copy(stackFrames = framesFilteredByObjects)
    }

    private fun createFile(baseDir: Path, flowId: StateMachineRunId): Path {
        val dir = baseDir / "flowStackSnapshots" / LocalDate.now().toString() / flowId.uuid.toString()
        val index = ThreadLocalIndex.currentIndex.get()
        val file = if (index == 0) dir / "flowStackSnapshot.json" else dir / "flowStackSnapshot-$index.json"
        ThreadLocalIndex.currentIndex.set(index + 1)
        return file
    }

    private class ThreadLocalIndex private constructor() {
        companion object {
            val currentIndex = object : ThreadLocal<Int>() {
                override fun initialValue() = 0
            }
        }
    }

}

private inline fun <reified R, A : Any> R.getField(name: String): A {
    val field = R::class.java.getDeclaredField(name)
    field.isAccessible = true
    return uncheckedCast(field.get(this))
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

/*
 * Returns pairs of (offset, size of frame)
 */
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

private const val MASK_FULL: Long = -1L

private fun getNumSlots(record: Long): Int {
    return getUnsignedBits(record, 14, 16).toInt()
}

private fun getUnsignedBits(word: Long, offset: Int, length: Int): Long {
    val a = 64 - length
    val b = a - offset
    return word.ushr(b) and MASK_FULL.ushr(a)
}
