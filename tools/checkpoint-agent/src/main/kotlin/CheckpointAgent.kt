package net.corda.tools

import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import javassist.ClassPool
import javassist.CtClass
import net.corda.core.internal.ThreadBox
import net.corda.core.utilities.ProgressTracker
import net.corda.tools.CheckpointAgent.Companion.instrumentClassname
import net.corda.tools.CheckpointAgent.Companion.instrumentType
import net.corda.tools.CheckpointAgent.Companion.log
import net.corda.tools.CheckpointAgent.Companion.maximumSize
import net.corda.tools.CheckpointAgent.Companion.minimumSize
import net.corda.tools.CheckpointAgent.Companion.stackDepth
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.lang.reflect.Field
import java.security.ProtectionDomain
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CheckpointAgent {

    // whether to instrument serialized object reads/writes or both
    enum class InstrumentationType {
        WRITE, READ, READ_WRITE
    }

    companion object {
        // custom argument defaults
        val DEFAULT_INSTRUMENT_CLASSNAME = "net.corda.node.services.statemachine.FlowStateMachineImpl"
        val DEFAULT_MINIMUM_SIZE = 8 * 1024
        val DEFAULT_MAXIMUM_SIZE = 1000 * 1024
        val DEFAULT_INSTRUMENT_TYPE = InstrumentationType.READ_WRITE
        val DEFAULT_STACK_DEPTH = 12

        // startup arguments
        var instrumentClassname = DEFAULT_INSTRUMENT_CLASSNAME
        var minimumSize = DEFAULT_MINIMUM_SIZE
        var maximumSize = DEFAULT_MAXIMUM_SIZE
        var instrumentType = DEFAULT_INSTRUMENT_TYPE
        var stackDepth = DEFAULT_STACK_DEPTH

        val log by lazy {
            LoggerFactory.getLogger("CheckpointAgent")
        }

        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {
            parseArguments(argumentsString)
            instrumentation.addTransformer(CheckpointHook)
        }

        fun parseArguments(argumentsString: String?) {
            argumentsString?.let {
                val nvpList = it.split(",")
                nvpList.forEach {
                    val nvpItem = it.split("=")
                    if (nvpItem.size == 2) {
                        when (nvpItem[0].trim()) {
                            "instrumentClassname" -> instrumentClassname = nvpItem[1]
                            "minimumSize" -> try { minimumSize = nvpItem[1].toInt() } catch (e: NumberFormatException) { println("Invalid value: ${nvpItem[1]}") }
                            "maximumSize" -> try { maximumSize = nvpItem[1].toInt() } catch (e: NumberFormatException) { println("Invalid value: ${nvpItem[1]}") }
                            "stackDepth" -> try { stackDepth = nvpItem[1].toInt() } catch (e: NumberFormatException) { println("Invalid value: ${nvpItem[1]}") }
                            "instrumentType" -> try { instrumentType = InstrumentationType.valueOf(nvpItem[1].toUpperCase()) } catch (e: Exception) { println("Invalid value: ${nvpItem[1]}") }
                            else -> println("Invalid argument: $nvpItem")
                        }
                    }
                    else println("Missing value for argument: $nvpItem")
                }
            }
            println("Running Checkpoint agent with following arguments: instrumentClassname = $instrumentClassname, instrumentType = $instrumentType, minimumSize = $minimumSize, maximumSize = $maximumSize, stackDepth = $stackDepth\n")
        }
    }
}

/**
 * The hook simply records the read() and write() entries and exits together with the output offset at the time of the call.
 * This is recorded in a StrandID -> List<StatsEvent> map.
 *
 * Later we "parse" these lists into a tree.
 */
object CheckpointHook : ClassFileTransformer {
    val classPool = ClassPool.getDefault()
    val hookClassName = javaClass.name

    override fun transform(
            loader: ClassLoader?,
            className: String,
            classBeingRedefined: Class<*>?,
            protectionDomain: ProtectionDomain?,
            classfileBuffer: ByteArray
    ): ByteArray? {
        if (className.startsWith("java") || className.startsWith("javassist") || className.startsWith("kotlin")) {
            return null
        }
        return try {
            val clazz = classPool.makeClass(ByteArrayInputStream(classfileBuffer))
            instrumentClass(clazz)?.toBytecode()
        } catch (throwable: Throwable) {
            throwable.printStackTrace(System.out)
            null
        }
    }

    private fun instrumentClass(clazz: CtClass): CtClass? {
        for (method in clazz.declaredBehaviors) {
            if (instrumentType == CheckpointAgent.InstrumentationType.READ_WRITE || instrumentType == CheckpointAgent.InstrumentationType.WRITE) {
                if (method.name == "write") {
                    val parameterTypeNames = method.parameterTypes.map { it.name }
                    if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Output", "java.lang.Object")) {
                        if (method.isEmpty) continue
                        log.debug("Instrumenting on write: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::writeEnter.name}($2, $3);")
                        method.insertAfter("$hookClassName.${this::writeExit.name}($2, $3);")
                        return clazz
                    }
                }
            }
            if (instrumentType == CheckpointAgent.InstrumentationType.READ_WRITE || instrumentType == CheckpointAgent.InstrumentationType.READ) {
                if (method.name == "read") {
                    val parameterTypeNames = method.parameterTypes.map { it.name }
                    if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Input", "java.lang.Class")) {
                        if (method.isEmpty) continue
                        log.debug("Instrumenting on read: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::readEnter.name}($2, $3);")
                        method.insertAfter("$hookClassName.${this::readExit.name}($2, $3, (java.lang.Object)\$_);")
                        return clazz
                    } else if (parameterTypeNames == listOf("com.esotericsoftware.kryo.io.Input", "java.lang.Object")) {
                        if (method.isEmpty) continue
                        log.debug("Instrumenting on field read: ${clazz.name}")
                        method.insertBefore("$hookClassName.${this::readFieldEnter.name}((java.lang.Object)this);")
                        method.insertAfter("$hookClassName.${this::readFieldExit.name}($2, (java.lang.Object)this);")
                        return clazz
                    }
                }
            }
        }
        return null
    }

    // StrandID -> StatsEvent map
    val events = ConcurrentHashMap<Long, Pair<ArrayList<StatsEvent>, AtomicInteger>>()

    // Global static variable that can be set programmatically by 3rd party tools/code (eg. CheckpointDumper)
    // Only relevant to READ operations (as the checkpoint id must have previously been generated)
    var checkpointId: UUID? = null
        set(value) {
            if (value != null)
                log.debug("Diagnosing checkpoint id: $value")
            mutex.locked {
                field = value
            }
        }
    private val mutex = ThreadBox(checkpointId)

    @JvmStatic
    fun readFieldEnter(that: Any) {
        if (that is FieldSerializer.CachedField<*>) {
            log.debug("readFieldEnter object: ${that.field.name}:${that.field.type}")
            val (list, _) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            list.add(StatsEvent.EnterField(that.field.name, that.field.type))
        }
    }

    @JvmStatic
    fun readFieldExit(obj: Any?, that: Any) {
        if (that is FieldSerializer.CachedField<*>) {
            val (list, _) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            val value = that.field.get(obj)
            val arrayValue = getArrayValue(that.field, value)
            if (!that.javaClass.name.endsWith("ObjectField")
                    || arrayValue != null || that.field.type == java.lang.String::class.java || value == null) {
                log.debug("readFieldExit basic type value: ${that.field.name}:${that.field.type} = ${arrayValue ?: value}")
                list.add(StatsEvent.BasicTypeField(that.field.name, that.field.type, arrayValue ?: value))
            } else {
                log.debug("readFieldExit object value: ${that.field.name}:${that.field.type} = $value")
                list.add(StatsEvent.ObjectField(that.field.name, that.field.type, value))
            }
        }
    }

    private fun getArrayValue(field: Field, value: Any?): Any? {
        if (field.type.isArray) {
            log.debug("readFieldExit array type: ${field.type}, value: $value]")
            if (field.type == CharArray::class.java) {
                val arrayValue = value as CharArray
                log.debug("readFieldExit char array: ${field.name}:${field.type} = ${arrayValue.joinToString("")}")
                return arrayValue.joinToString("")
            }
            else if (field.type == Array<Char>::class.java) {
                val arrayValue = value as Array<Char>
                log.debug("readFieldExit array of char: ${field.name}:${field.type} = ${arrayValue.joinToString("")}")
                return arrayValue.joinToString("")
            }
            else if (field.type == ByteArray::class.java) {
                val arrayValue = value as ByteArray
                log.debug("readFieldExit byte array: ${field.name}:${field.type} = ${byteArrayToHex(arrayValue)}")
                return byteArrayToHex(arrayValue)
            }
            else if (field.type == Array<Byte>::class.java) {
                val arrayValue = value as Array<Byte>
                log.debug("readFieldExit array of byte: ${field.name}:${field.type} = ${byteArrayToHex(arrayValue.toByteArray())}")
                return byteArrayToHex(arrayValue.toByteArray())
            }
            else if (field.type == ShortArray::class.java) {
                val arrayValue = value as ShortArray
                log.debug("readFieldExit short array: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == Array<Short>::class.java) {
                val arrayValue = value as Array<Short>
                log.debug("readFieldExit array of short: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == IntArray::class.java) {
                val arrayValue = value as IntArray
                log.debug("readFieldExit int array: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == Array<Int>::class.java) {
                val arrayValue = value as Array<Int>
                log.debug("readFieldExit array of Int: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == LongArray::class.java) {
                val arrayValue = value as LongArray
                log.debug("readFieldExit long array: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == Array<Long>::class.java) {
                val arrayValue = value as Array<Long>
                log.debug("readFieldExit array of long: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == FloatArray::class.java) {
                val arrayValue = value as FloatArray
                log.debug("readFieldExit float array: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == Array<Float>::class.java) {
                val arrayValue = value as Array<Float>
                log.debug("readFieldExit array of float: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == DoubleArray::class.java) {
                val arrayValue = value as DoubleArray
                log.debug("readFieldExit double array: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == Array<Double>::class.java) {
                val arrayValue = value as Array<Double>
                log.debug("readFieldExit array of double: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == BooleanArray::class.java) {
                val arrayValue = value as BooleanArray
                log.debug("readFieldExit boolean array: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == Array<Boolean>::class.java) {
                val arrayValue = value as Array<Boolean>
                log.debug("readFieldExit array of boolean: ${field.name}:${field.type} = ${arrayValue.joinToString(",")}")
                return arrayValue.joinToString(",")
            }
            else if (field.type == arrayOf<Array<Int>>()::class.java) {
                val arrayValue = value as Array<Array<Int>>
                val arrayValueOuter =
                        arrayValue.map {
                            it.joinToString { "$it" }
                        }
                log.debug("readFieldExit 2D array of int: ${field.name}:${field.type} = ${arrayValueOuter.joinToString(",", prefix = "[", postfix = "]")}")
                return arrayValueOuter.joinToString(",", prefix = "[", postfix = "]")
            }
            else if (field.type == arrayOf<Array<Array<Int>>>()::class.java) {
                val arrayValue = value as Array<Array<Array<Int>>>
                val arrayValueOuter =
                        arrayValue.flatMap { inner ->
                            inner.map {
                                it.joinToString { "$it" }
                            }
                        }
                log.debug("readFieldExit 3D array of int: ${field.name}:${field.type} = ${arrayValueOuter.joinToString(",", prefix = "[", postfix = "]")}")
                return arrayValueOuter.joinToString(",", prefix = "[", postfix = "]")
            }
            log.debug("ARRAY OF TYPE: ${field.type} (size: ${(value as Array<Any?>).size})")
        }
        return null
    }

    // https://stackoverflow.com/questions/9655181/how-to-convert-a-byte-array-to-a-hex-string-in-java
    private fun byteArrayToHex(a: ByteArray): String {
        val sb = StringBuilder(a.size * 2)
        for (b in a)
            sb.append(String.format("%02x", b))
        return sb.toString()
    }

    @JvmStatic
    fun readEnter(input: Input, clazz: Class<*>) {
        val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        log.debug("readEnter: adding event for clazz: ${clazz.name} (strandId: ${Strand.currentStrand().id})")
        list.add(StatsEvent.Enter(clazz.name, input.total()))
        count.incrementAndGet()
    }

    @JvmStatic
    fun readExit(input: Input, clazz: Class<*>, value: Any?) {
        val (list, count) = events[Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(clazz.name, input.total(), value))
        log.debug("readExit: clazz[$clazz], strandId[${Strand.currentStrand().id}], eventCount[$count]")
        if (count.decrementAndGet() == 0) {
            if ((checkpointId != null) ||
                    ((clazz.name == instrumentClassname) &&
                            (input.total() >= minimumSize) &&
                            (input.total() <= maximumSize))) {
                val sb = StringBuilder()
                if (checkpointId != null)
                    sb.append("Checkpoint id: $checkpointId\n")
                prettyStatsTree(0, StatsInfo("", Any::class.java), readTree(list, 0).second, sb)

                log.info("[READ] $clazz\n$sb")
                checkpointId = null
            }
            log.debug("readExit: clearing event for clazz: $clazz (strandId: ${Strand.currentStrand().id})")
            events.remove(Strand.currentStrand().id)
        }
    }

    @JvmStatic
    fun writeEnter(output: Output, obj: Any) {
        val (list, count) = events.getOrPut(-Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        log.debug("writeEnter: adding event for clazz: ${obj.javaClass.name} (strandId: ${Strand.currentStrand().id})")
        list.add(StatsEvent.Enter(obj.javaClass.name, output.total()))
        count.incrementAndGet()
    }

    @JvmStatic
    fun writeExit(output: Output, obj: Any) {
        val (list, count) = events[-Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(obj.javaClass.name, output.total(), null))
        log.debug("writeExit: clazz[${obj.javaClass.name}], strandId[${Strand.currentStrand().id}], eventCount[$count]")
        if (count.decrementAndGet() == 0) {
            // always log diagnostics for explicit checkpoint ids (eg. set dumpCheckpoints)
            if ((checkpointId != null) ||
                    ((obj.javaClass.name == instrumentClassname) &&
                            (output.total() >= minimumSize) &&
                            (output.total() <= maximumSize))) {
                val sb = StringBuilder()
                prettyStatsTree(0, StatsInfo("", Any::class.java), readTree(list, 0).second, sb)
                log.info("[WRITE] $obj\n$sb")
                checkpointId = null
            }
            log.debug("writeExit: clearing event for clazz: ${obj.javaClass.name} (strandId: ${Strand.currentStrand().id})")
            events.remove(-Strand.currentStrand().id)
        }
    }

    private fun prettyStatsTree(indent: Int, statsInfo: StatsInfo, statsTree: StatsTree, builder: StringBuilder) {
        when (statsTree) {
            is StatsTree.Object -> {
                if (indent/2  < stackDepth) {
                    builder.append(String.format("%03d:", indent / 2))
                    builder.append(CharArray(indent) { ' ' })
                    builder.append(" ${statsInfo.fieldName} ")
                    if (statsInfo.fieldType != null && statsInfo.fieldType.isArray)
                        builder.append("${statsInfo.fieldType} array of size:${(statsTree.value as Array<Any?>).size}")
                    else
                        builder.append("${statsTree.className}")
                    builder.append(" ")
                    builder.append(String.format("%,d", statsTree.size))
                    builder.append("\n")
                }
                if (statsInfo.fieldType != ProgressTracker::class.java)
                    for (child in statsTree.children) {
                        prettyStatsTree(indent + 2, child.first, child.second, builder)
                    }
            }
            is StatsTree.BasicType -> {
                if (indent/2 < stackDepth) {
                    builder.append(String.format("%03d:", indent / 2))
                    builder.append(CharArray(indent) { ' ' })
                    builder.append(" ${statsInfo.fieldName} ")
                    builder.append("${statsTree.value}")
                    builder.append("\n")
                }
            }
        }
    }

}

sealed class StatsEvent {
    data class Enter(val className: String, val offset: Long) : StatsEvent()
    data class Exit(val className: String, val offset: Long, val value: Any?) : StatsEvent()
    data class BasicTypeField(val fieldName: String, val fieldType: Class<*>?, val fieldValue: Any?) : StatsEvent()
    data class EnterField(val fieldName: String, val fieldType: Class<*>?) : StatsEvent()
    data class ObjectField(val fieldName: String, val fieldType: Class<*>?, val value: Any) : StatsEvent()
}

sealed class StatsTree {
    data class Object(
            val className: String,
            val size: Long,
            val children: List<Pair<StatsInfo, StatsTree>>,
            var count: Int,
            val value: Any?
    ) : StatsTree()

    data class BasicType(
            val value: Any?
    ) : StatsTree()

    data class Loop(var depth: Int) : StatsTree() {
        override fun toString(): String {
            return "Loop()"
        }
    }
}

fun readTree(events: List<StatsEvent>, index: Int, idMap: IdentityHashMap<Any, StatsTree> = IdentityHashMap()): Pair<Int, StatsTree> {
    val event = events[index]
    when (event) {
        is StatsEvent.Enter -> {
            val (nextIndex, children) = readTrees(events, index + 1, idMap)
            val exit = events[nextIndex] as StatsEvent.Exit
            require(event.className == exit.className)
            val tree = StatsTree.Object(event.className, exit.offset - event.offset, children, 0, exit.value)
            idMap[exit.value] = tree
            return Pair(nextIndex + 1, tree)
        }
        else -> {
            throw IllegalStateException("Wasn't expecting event: $event")
        }
    }
}

data class StatsInfo(val fieldName: String, val fieldType: Class<*>?)

fun readTrees(events: List<StatsEvent>, index: Int, idMap: IdentityHashMap<Any, StatsTree>): Pair<Int, List<Pair<StatsInfo, StatsTree>>> {
    val trees = ArrayList<Pair<StatsInfo, StatsTree>>()
    var i = index
    var inField = false
    while (true) {
        val event = events.getOrNull(i)
        when (event) {
            is StatsEvent.Enter -> {
                val (nextIndex, tree) = readTree(events, i, idMap)
                if (!inField)
                    trees += StatsInfo("", Any::class.java) to tree
                i = nextIndex
            }
            is StatsEvent.EnterField -> {
                i++
                inField = true
            }
            is StatsEvent.Exit -> {
                return Pair(i, trees)
            }
            is StatsEvent.BasicTypeField -> {
                trees += StatsInfo(event.fieldName, event.fieldType) to StatsTree.BasicType(event.fieldValue)
                i++
                inField = false
            }
            is StatsEvent.ObjectField -> {
                val tree = idMap[event.value] ?: StatsTree.Loop(0)
                if (tree is StatsTree.Object)
                    tree.count++
                if (tree is StatsTree.Loop)
                    tree.depth++
                trees += StatsInfo(event.fieldName, event.fieldType) to tree
                i++
                inField = false
            }
            null -> {
                return Pair(i, trees)
            }
        }
    }
}
