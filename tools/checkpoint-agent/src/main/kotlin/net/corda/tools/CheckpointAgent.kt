package net.corda.tools

import co.paralleluniverse.strands.Strand
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.FieldSerializer
import javassist.ClassPool
import javassist.CtClass
import net.corda.core.internal.ThreadBox
import net.corda.core.utilities.debug
import net.corda.tools.CheckpointAgent.Companion.graphDepth
import net.corda.tools.CheckpointAgent.Companion.instrumentClassname
import net.corda.tools.CheckpointAgent.Companion.instrumentType
import net.corda.tools.CheckpointAgent.Companion.log
import net.corda.tools.CheckpointAgent.Companion.maximumSize
import net.corda.tools.CheckpointAgent.Companion.minimumSize
import net.corda.tools.CheckpointAgent.Companion.printOnce
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class CheckpointAgent {

    // whether to instrument serialized object on reads or write
    enum class InstrumentationType {
        WRITE, READ
    }

    companion object {
        // custom argument defaults
        val DEFAULT_INSTRUMENT_CLASSNAME = "net.corda.node.services.statemachine.FlowStateMachineImpl"
        val DEFAULT_INSTRUMENT_TYPE = InstrumentationType.READ
        val DEFAULT_MINIMUM_SIZE = 8 * 1024
        val DEFAULT_MAXIMUM_SIZE = 20_000_000
        val DEFAULT_GRAPH_DEPTH = Int.MAX_VALUE

        // startup arguments
        var instrumentClassname = DEFAULT_INSTRUMENT_CLASSNAME
        var minimumSize = DEFAULT_MINIMUM_SIZE
        var maximumSize = DEFAULT_MAXIMUM_SIZE
        var instrumentType = DEFAULT_INSTRUMENT_TYPE
        var graphDepth = DEFAULT_GRAPH_DEPTH
        var printOnce = true

        val log by lazy {
            LoggerFactory.getLogger("CheckpointAgent")
        }

        val running by lazy {
            premainExecuted
        }
        private var premainExecuted = false

        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {
            premainExecuted = true
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
                            "instrumentType" -> try {
                                instrumentType = InstrumentationType.valueOf(nvpItem[1].toUpperCase())
                            } catch (e: Exception) {
                                display("Invalid value: ${nvpItem[1]}. Please specify read or write.")
                            }
                            "minimumSize" -> try {
                                minimumSize = nvpItem[1].toInt()
                            } catch (e: NumberFormatException) {
                                display("Invalid value: ${nvpItem[1]}. Please specify an integer value.")
                            }
                            "maximumSize" -> try {
                                maximumSize = nvpItem[1].toInt()
                            } catch (e: NumberFormatException) {
                                display("Invalid value: ${nvpItem[1]}. Please specify an integer value.")
                            }
                            "graphDepth" -> try {
                                graphDepth = nvpItem[1].toInt()
                            } catch (e: NumberFormatException) {
                                display("Invalid value: ${nvpItem[1]}. Please specify an integer value.")
                            }
                            "printOnce" -> try {
                                printOnce = nvpItem[1].toBoolean()
                            } catch (e: Exception) {
                                display("Invalid value: ${nvpItem[1]}. Please specify true or false.")
                            }
                            else -> display("Invalid argument: $nvpItem")
                        }
                    } else display("Missing value for argument: $nvpItem")
                }
            }
            println("Running Checkpoint agent with following arguments: instrumentClassname=$instrumentClassname, instrumentType=$instrumentType, minimumSize=$minimumSize, maximumSize=$maximumSize, graphDepth=$graphDepth, printOnce=$printOnce\n")
        }

        private fun display(output: String) {
            System.err.println("CheckpointAgent: $output")
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
            if (instrumentType == CheckpointAgent.InstrumentationType.WRITE) {
                if (method.name == "write") {
                    val parameterTypeNames = method.parameterTypes.map { it.name }
                    if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Output", "java.lang.Object")) {
                        if (method.isEmpty) continue
                        log.debug { "Instrumenting on write: ${clazz.name}" }
                        method.insertBefore("$hookClassName.${this::writeEnter.name}($2, $3);")
                        method.insertAfter("$hookClassName.${this::writeExit.name}($2, $3);")
                        return clazz
                    }
                }
            }
            if (instrumentType == CheckpointAgent.InstrumentationType.READ) {
                if (method.name == "read") {
                    val parameterTypeNames = method.parameterTypes.map { it.name }
                    if (parameterTypeNames == listOf("com.esotericsoftware.kryo.Kryo", "com.esotericsoftware.kryo.io.Input", "java.lang.Class")) {
                        if (method.isEmpty) continue
                        log.debug { "Instrumenting on read: ${clazz.name}" }
                        method.insertBefore("$hookClassName.${this::readEnter.name}($2, $3);")
                        method.insertAfter("$hookClassName.${this::readExit.name}($2, $3, (java.lang.Object)\$_);")
                        return clazz
                    } else if (parameterTypeNames == listOf("com.esotericsoftware.kryo.io.Input", "java.lang.Object")) {
                        if (method.isEmpty) continue
                        log.debug { "Instrumenting on field read: ${clazz.name}" }
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
                log.debug { "Diagnosing checkpoint id: $value" }
            mutex.locked {
                field = value
            }
        }
    private val mutex = ThreadBox(checkpointId)

    @JvmStatic
    fun readFieldEnter(that: Any) {
        if (that is FieldSerializer.CachedField<*>) {
            log.debug { "readFieldEnter object: ${that.field.name}:${that.field.type}" }
            val (list, _) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            list.add(StatsEvent.EnterField(that.field.name, that.field.type))
        }
    }

    @JvmStatic
    fun readFieldExit(obj: Any?, that: Any) {
        if (that is FieldSerializer.CachedField<*>) {
            val (list, _) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
            val value = that.field.get(obj)
            val arrayValue = getArrayValue(that.field.type, value)
            if (!that.javaClass.name.endsWith("ObjectField")
                    || arrayValue != null || that.field.type == java.lang.String::class.java || value == null) {
                log.debug { "readFieldExit basic type value: ${that.field.name}:${that.field.type} = ${arrayValue ?: value}" }
                list.add(StatsEvent.BasicTypeField(that.field.name, that.field.type, arrayValue ?: value))
            } else {
                log.debug { "readFieldExit object value: ${that.field.name}:${that.field.type} = $value" }
                list.add(StatsEvent.ObjectField(that.field.name, that.field.type, value))
            }
        }
    }

    private fun <T> getArrayValue(clazz: Class<T>, value: Any?): String? {
        if (clazz.isArray) {
            log.debug { "readFieldExit array type: $clazz, value: $value]" }
            @Suppress("UNCHECKED_CAST")
            if (Array<Number>::class.java.isAssignableFrom(clazz)) {
                @Suppress("UNCHECKED_CAST")
                val numberValue = value as Array<Number>
                log.debug { "readFieldExit array of number: $clazz = ${numberValue.joinToString(",")}" }
                return numberValue.joinToString(",")
            } else if (clazz == Array<Boolean>::class.java) {
                @Suppress("UNCHECKED_CAST")
                val arrayValue = value as Array<Boolean>
                log.debug { "readFieldExit array of boolean: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            }
            // N dimensional arrays
            else if (arrayOf<Array<*>>()::class.java.isAssignableFrom(clazz)) {
                @Suppress("UNCHECKED_CAST")
                val arrayValue = value as Array<Array<*>>
                return arrayValue.map { arrayEntry ->
                    log.debug { "N Dimensional: $clazz, $arrayEntry, ${arrayEntry::class.java}" }
                    "[" + getArrayValue(arrayEntry::class.java, arrayEntry) + "]"
                }.toString()
            }
            // Kotlin array types
            else if (clazz == CharArray::class.java) {
                val arrayValue = value as CharArray
                log.debug { "readFieldExit char array: $clazz = ${arrayValue.joinToString("")}" }
                return arrayValue.joinToString("")
            } else if (clazz == ByteArray::class.java) {
                val arrayValue = value as ByteArray
                log.debug { "readFieldExit byte array: $clazz = ${byteArrayToHex(arrayValue)}" }
                return byteArrayToHex(arrayValue)
            } else if (clazz == ShortArray::class.java) {
                val arrayValue = value as ShortArray
                log.debug { "readFieldExit short array: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            } else if (clazz == IntArray::class.java) {
                val arrayValue = value as IntArray
                log.debug { "readFieldExit int array: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            } else if (clazz == LongArray::class.java) {
                val arrayValue = value as LongArray
                log.debug { "readFieldExit long array: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            } else if (clazz == FloatArray::class.java) {
                val arrayValue = value as FloatArray
                log.debug { "readFieldExit float array: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            } else if (clazz == DoubleArray::class.java) {
                val arrayValue = value as DoubleArray
                log.debug { "readFieldExit double array: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            } else if (clazz == BooleanArray::class.java) {
                val arrayValue = value as BooleanArray
                log.debug { "readFieldExit boolean array: $clazz = ${arrayValue.joinToString(",")}" }
                return arrayValue.joinToString(",")
            }
            @Suppress("UNCHECKED_CAST")
            log.debug { "ARRAY OF TYPE: $clazz (size: ${(value as Array<Any?>).size})" }
        }
        return null
    }

    private fun byteArrayToHex(a: ByteArray): String {
        val sb = StringBuilder(a.size * 2)
        for (b in a)
            sb.append(String.format("%02x", b))
        return sb.toString()
    }

    @JvmStatic
    fun readEnter(input: Input, clazz: Class<*>) {
        val (list, count) = events.getOrPut(Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        log.debug { "readEnter: adding event for clazz: ${clazz.name} (strandId: ${Strand.currentStrand().id})" }
        list.add(StatsEvent.Enter(clazz.name, input.total()))
        count.incrementAndGet()
    }

    @JvmStatic
    fun readExit(input: Input, clazz: Class<*>, value: Any?) {
        val (list, count) = events[Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(clazz.name, input.total(), value))
        log.debug { "readExit: clazz[$clazz], strandId[${Strand.currentStrand().id}], eventCount[$count]" }
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
            log.debug { "readExit: clearing event for clazz: $clazz (strandId: ${Strand.currentStrand().id})" }
            events.remove(Strand.currentStrand().id)
        }
    }

    @JvmStatic
    fun writeEnter(output: Output, obj: Any) {
        val (list, count) = events.getOrPut(-Strand.currentStrand().id) { Pair(ArrayList(), AtomicInteger(0)) }
        log.debug { "writeEnter: adding event for clazz: ${obj.javaClass.name} (strandId: ${Strand.currentStrand().id})" }
        list.add(StatsEvent.Enter(obj.javaClass.name, output.total()))
        count.incrementAndGet()
    }

    @JvmStatic
    fun writeExit(output: Output, obj: Any) {
        val (list, count) = events[-Strand.currentStrand().id]!!
        list.add(StatsEvent.Exit(obj.javaClass.name, output.total(), null))
        log.debug { "writeExit: clazz[${obj.javaClass.name}], strandId[${Strand.currentStrand().id}], eventCount[$count]" }
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
            log.debug { "writeExit: clearing event for clazz: ${obj.javaClass.name} (strandId: ${Strand.currentStrand().id})" }
            events.remove(-Strand.currentStrand().id)
        }
    }

    private fun prettyStatsTree(indent: Int, statsInfo: StatsInfo, identityInfo: IdentityInfo, builder: StringBuilder) {
        val statsTree = identityInfo.tree
        when (statsTree) {
            is StatsTree.Object -> {
                if (printOnce && identityInfo.refCount > 1) {
                    log.debug { "Skipping $statsInfo, $statsTree (count:${identityInfo.refCount})" }
                } else if (indent / 2 < graphDepth) {
                    builder.append(String.format("%03d:", indent / 2))
                    builder.append(CharArray(indent) { ' ' })
                    builder.append(" ${statsInfo.fieldName} ")
                    if (statsInfo.fieldType != null && statsInfo.fieldType.isArray) {
                        @Suppress("UNCHECKED_CAST")
                        val arrayValue = (statsTree.value as Array<Any?>)
                        builder.append("${statsInfo.fieldType} (array length:${arrayValue.size})")
                    } else if (statsInfo.fieldType != null && statsTree.value is Collection<*>) {
                        builder.append("${statsInfo.fieldType} (collection size:${statsTree.value.size})")
                    } else if (statsInfo.fieldType != null && statsTree.value is Map<*, *>) {
                        builder.append("${statsInfo.fieldType} (map size:${statsTree.value.size})")
                    } else {
                        builder.append("${statsTree.className} (hash:${statsTree.value?.hashCode()}) (count:${identityInfo.refCount})")
                    }
                    builder.append(" ")
                    builder.append(String.format("%,d", statsTree.size))
                    builder.append("\n")
                }
                for (child in statsTree.children) {
                    prettyStatsTree(indent + 2, child.first, child.second, builder)
                }
            }
            is StatsTree.BasicType -> {
                if (indent / 2 < graphDepth) {
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
            val children: List<Pair<StatsInfo, IdentityInfo>>,
            val value: Any?
    ) : StatsTree() {
        override fun toString(): String {
            return "Object [$className, $size, $value]"
        }
    }

    data class BasicType(
            val value: Any?
    ) : StatsTree()

    data class Loop(var depth: Int) : StatsTree() {
        override fun toString(): String {
            return "Loop()"
        }
    }
}

data class IdentityInfo(val tree: StatsTree, val refCount: Int)

fun readTree(events: List<StatsEvent>, index: Int, idMap: IdentityHashMap<Any, IdentityInfo> = IdentityHashMap()): Pair<Int, IdentityInfo> {
    val event = events[index]
    when (event) {
        is StatsEvent.Enter -> {
            val (nextIndex, children) = readTrees(events, index + 1, idMap)
            val exit = events[nextIndex] as StatsEvent.Exit
            require(event.className == exit.className)
            val tree = StatsTree.Object(event.className, exit.offset - event.offset, children, exit.value)
            if (idMap.containsKey(exit.value)) {
                val identityInfo = idMap[exit.value]!!
                idMap[exit.value] = IdentityInfo(identityInfo.tree, identityInfo.refCount + 1)
                log.debug { "Skipping repeated StatsEvent.Enter: ${exit.value} (hashcode:${exit.value!!.hashCode()}) (count:${idMap[exit.value]?.refCount})" }
            } else idMap[exit.value] = IdentityInfo(tree, 1)
            return Pair(nextIndex + 1, idMap[exit.value]!!)
        }
        else -> {
            throw IllegalStateException("Wasn't expecting event: $event")
        }
    }
}

data class StatsInfo(val fieldName: String, val fieldType: Class<*>?)

fun readTrees(events: List<StatsEvent>, index: Int, idMap: IdentityHashMap<Any, IdentityInfo>): Pair<Int, List<Pair<StatsInfo, IdentityInfo>>> {
    val trees = ArrayList<Pair<StatsInfo, IdentityInfo>>()
    var i = index
    var arrayIdx = 0
    var inField = false
    while (true) {
        val event = events.getOrNull(i)
        when (event) {
            is StatsEvent.Enter -> {
                val (nextIndex, tree) = readTree(events, i, idMap)
                if (!inField) {
                    arrayIdx++
                    trees += StatsInfo("[$arrayIdx]", Any::class.java) to tree
                }
                i = nextIndex
            }
            is StatsEvent.EnterField -> {
                i++
                inField = true
            }
            is StatsEvent.Exit -> {
                if (idMap.containsKey(event.value)) {
                    val identityInfo = idMap[event.value]!!
                    idMap[event.value] = IdentityInfo(identityInfo.tree, identityInfo.refCount + 1)
                    log.debug { "Skipping repeated StatsEvent.Exit: ${event.value} (hashcode:${event.value!!.hashCode()}) (count:${idMap[event.value]?.refCount})" }
                }
                return Pair(i, trees)
            }
            is StatsEvent.BasicTypeField -> {
                trees += StatsInfo(event.fieldName, event.fieldType) to IdentityInfo(StatsTree.BasicType(event.fieldValue), 1)
                i++
                inField = false
            }
            is StatsEvent.ObjectField -> {
                val identityInfo =
                        if (idMap.containsKey(event.value)) {
                            val identityInfo = idMap[event.value]!!
                            idMap[event.value] = IdentityInfo(identityInfo.tree, identityInfo.refCount + 1)
                            log.debug { "Skipping repeated StatsEvent.ObjectField: ${event.value} (hashcode:${event.value.hashCode()}) (count:${idMap[event.value]?.refCount})" }
                            identityInfo
                        } else {
                            IdentityInfo(StatsTree.Loop(0), 1)
                        }
                trees += StatsInfo(event.fieldName, event.fieldType) to identityInfo
                i++
                inField = false
            }
            null -> {
                return Pair(i, trees)
            }
        }
    }
}
