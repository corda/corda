package net.corda.quasarhook

import javassist.ClassPool
import javassist.CtClass
import java.lang.instrument.ClassFileTransformer
import java.lang.instrument.Instrumentation
import java.security.ProtectionDomain
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Used to collect classes through instrumentation.
 */
class ClassRecorder {
    val usedInstrumentedClasses = ConcurrentHashMap<String, Unit>()
    val instrumentedClasses = ConcurrentHashMap<String, Unit>()
    val scannedClasses = ConcurrentHashMap<String, Unit>()
}

/**
 * Use global state to do the collection.
 */
val classRecorder = ClassRecorder()

/**
 * This is a hook called from each quasar getStack call, which happens on suspension. We construct a callstack and
 * extract the part of the stack between the quasar scheduler and the getStack call, which should contain all methods/classes
 * relevant to this suspension.
 */
fun recordUsedInstrumentedCallStack() {
    val throwable = Throwable()
    var index = 0
    while (true) {
        require(index < throwable.stackTrace.size) { "Can't find getStack call" }
        val stackElement = throwable.stackTrace[index]
        if (stackElement.className == "co.paralleluniverse.fibers.Stack" && stackElement.methodName == "getStack") {
            break
        }
        index++
    }
    index++
    while (index < throwable.stackTrace.size) {
        val stackElement = throwable.stackTrace[index]
        if (stackElement.className.startsWith("co.paralleluniverse")) {
            break
        }
        classRecorder.usedInstrumentedClasses[stackElement.className] = Unit
        index++
    }
}

/**
 * This is a hook called from the method instrumentor visitor. Note that this should only be called once we're sure
 * instrumentation will happen.
 */
fun recordInstrumentedClass(className: String) {
    classRecorder.instrumentedClasses[className] = Unit
}

/**
 * This is a hook called from QuasarInstrumentor, after the exclude filtering, but before examining the bytecode.
 */
fun recordScannedClass(className: String?) {
    if (className != null) {
        classRecorder.scannedClasses[className] = Unit
    }
}

/**
 * Arguments to this javaagent.
 *
 * @param truncate A comma-separated list of packages to trim from the exclude patterns.
 * @param expand A comma-separated list of packages to expand in the glob output. This is useful for certain top-level
 *     domains that we don't want to completely exclude, because later on classes may be loaded from those namespaces
 *     that require instrumentation.
 * @param alwaysExcluded A comma-separated list of packages under which all touched classes will be excluded.
 * @param separator The package part separator character used in the above lists.
 */
data class Arguments(
        val truncate: List<String>? = null,
        val expand: List<String>? = null,
        val alwaysExcluded: List<String>? = null,
        val separator: Char = '.'
)

/**
 * This javaagent instruments quasar to extract information about what classes are scanned, instrumented, and used at
 * runtime. On process exit the javaagent tries to calculate what an appropriate exclude pattern should be.
 */
class QuasarInstrumentationHookAgent {
    companion object {
        @JvmStatic
        fun premain(argumentsString: String?, instrumentation: Instrumentation) {

            var arguments = Arguments()
            argumentsString?.let {
                it.split(";").forEach {
                    val (key, value) = it.split("=")
                    when (key) {
                        "truncate" -> arguments = arguments.copy(truncate = value.split(","))
                        "expand" -> arguments = arguments.copy(expand = value.split(","))
                        "alwaysExcluded" -> arguments = arguments.copy(alwaysExcluded = value.split(","))
                        "separator" -> arguments = arguments.copy(separator = value.toCharArray()[0])
                    }
                }
            }

            Runtime.getRuntime().addShutdownHook(Thread {
                println("Instrumented classes: ${classRecorder.instrumentedClasses.size}")
                classRecorder.instrumentedClasses.forEach {
                    println("  $it")
                }
                println("Used instrumented classes: ${classRecorder.usedInstrumentedClasses.size}")
                classRecorder.usedInstrumentedClasses.forEach {
                    println("  $it")
                }
                println("Scanned classes: ${classRecorder.scannedClasses.size}")
                classRecorder.scannedClasses.keys.take(20).forEach {
                    println("  $it")
                }
                println("  (...)")
                val scannedTree = PackageTree.fromStrings(classRecorder.scannedClasses.keys.toList(), '/')
                val instrumentedTree = PackageTree.fromStrings(classRecorder.instrumentedClasses.keys.toList(), '/')
                val alwaysExclude = arguments.alwaysExcluded?.let { PackageTree.fromStrings(it, arguments.separator) }
                val alwaysExcludedTree = alwaysExclude?.let { instrumentedTree.truncate(it) } ?: instrumentedTree
                println("Suggested exclude globs:")
                val truncate = arguments.truncate?.let { PackageTree.fromStrings(it, arguments.separator) }
                // The separator append is a hack, it causes a package with an empty name to be added to the exclude tree,
                // which practically causes that level of the tree to be always expanded in the output globs.
                val expand = arguments.expand?.let { PackageTree.fromStrings(it.map { "$it${arguments.separator}" }, arguments.separator) }
                val truncatedTree = truncate?.let { scannedTree.truncate(it) } ?: scannedTree
                val expandedTree = expand?.let { alwaysExcludedTree.merge(it) } ?: alwaysExcludedTree
                val globs = truncatedTree.toGlobs(expandedTree)
                globs.forEach {
                    println("  $it")
                }
                println("Quasar exclude expression:")
                println("  x(${globs.joinToString(";")})")
            })
            instrumentation.addTransformer(QuasarInstrumentationHook)
        }
    }

}

object QuasarInstrumentationHook : ClassFileTransformer {
    val classPool = ClassPool.getDefault()!!

    const val hookClassName = "net.corda.quasarhook.QuasarInstrumentationHookKt"

    val instrumentMap = mapOf<String, (CtClass) -> Unit>(
            "co/paralleluniverse/fibers/Stack" to { clazz ->
                // This is called on each suspend, we hook into it to get the stack trace of actually used Suspendables
                val getStackMethod = clazz.methods.single { it.name == "getStack" }
                getStackMethod.insertBefore(
                        "$hookClassName.${::recordUsedInstrumentedCallStack.name}();"
                )
            },
            "co/paralleluniverse/fibers/instrument/InstrumentMethod" to { clazz ->
                // This is called on each instrumented method
                val acceptMethod = clazz.declaredMethods.single { it.name == "collectCodeBlocks" }
                acceptMethod.insertBefore(
                        "$hookClassName.${::recordInstrumentedClass.name}(this.className);"
                )
            },
            "co/paralleluniverse/fibers/instrument/QuasarInstrumentor" to { clazz ->
                val instrumentClassMethods = clazz.methods.filter {
                    it.name == "instrumentClass"
                }
                // TODO this is very brittle, we want to match on a specific instrumentClass() function. We could use the function signature, but that may change between versions anyway. Why is this function overloaded??
                instrumentClassMethods[0].insertBefore(
                        "$hookClassName.${::recordScannedClass.name}(className);"
                )
            }
    )

    override fun transform(
            loader: ClassLoader?,
            className: String,
            classBeingRedefined: Class<*>?,
            protectionDomain: ProtectionDomain?,
            classfileBuffer: ByteArray
    ): ByteArray {
        return try {
            val instrument = instrumentMap[className]
            return instrument?.let {
                val clazz = classPool.makeClass(classfileBuffer.inputStream())
                it(clazz)
                clazz.toBytecode()
            } ?: classfileBuffer
        } catch (throwable: Throwable) {
            println("SOMETHING WENT WRONG")
            throwable.printStackTrace(System.out)
            classfileBuffer
        }
    }
}

data class Glob(val parts: List<String>, val isFull: Boolean) {
    override fun toString(): String {
        return if (isFull) {
            parts.joinToString(".")
        } else {
            "${parts.joinToString(".")}**"
        }
    }
}

/**
 * Build up a tree from parts of the package names.
 */
data class PackageTree(val branches: Map<String, PackageTree>) {
    fun isEmpty() = branches.isEmpty()

    /**
     * Merge the tree with [other].
     */
    fun merge(other: PackageTree): PackageTree {
        val mergedBranches = HashMap(branches)
        other.branches.forEach { (key, tree) ->
            mergedBranches.compute(key) { _, previousTree ->
                previousTree?.merge(tree) ?: tree
            }
        }
        return PackageTree(mergedBranches)
    }

    /**
     * Truncate the tree below [other].
     */
    fun truncate(other: PackageTree): PackageTree {
        return if (other.isEmpty()) {
            empty
        } else {
            val truncatedBranches = HashMap(branches)
            other.branches.forEach { (key, tree) ->
                truncatedBranches.compute(key) { _, previousTree ->
                    previousTree?.truncate(tree) ?: empty
                }
            }
            PackageTree(truncatedBranches)
        }
    }

    companion object {
        val empty = PackageTree(emptyMap())
        fun fromString(fullClassName: String, separator: Char): PackageTree {
            var current = empty
            fullClassName.split(separator).reversed().forEach {
                current = PackageTree(mapOf(it to current))
            }
            return current
        }

        fun fromStrings(fullClassNames: List<String>, separator: Char): PackageTree {
            return mergeAll(fullClassNames.map { PackageTree.fromString(it, separator) })
        }

        fun mergeAll(trees: List<PackageTree>): PackageTree {
            return trees.foldRight(PackageTree.empty, PackageTree::merge)
        }
    }

    /**
     * Construct minimal globs that match this tree but don't match [excludeTree].
     */
    fun toGlobs(excludeTree: PackageTree): List<Glob> {
        data class State(
                val include: PackageTree,
                val exclude: PackageTree,
                val globSoFar: List<String>
        )

        val toExpandList = LinkedList(listOf(State(this, excludeTree, emptyList())))
        val globs = ArrayList<Glob>()
        while (true) {
            val state = toExpandList.pollFirst() ?: break
            if (state.exclude.branches.isEmpty()) {
                globs.add(Glob(state.globSoFar, state.include.isEmpty()))
            } else {
                state.include.branches.forEach { (key, subTree) ->
                    val excludeSubTree = state.exclude.branches[key]
                    if (excludeSubTree != null) {
                        toExpandList.addLast(State(subTree, excludeSubTree, state.globSoFar + key))
                    } else {
                        globs.add(Glob(state.globSoFar + key, subTree.isEmpty()))
                    }
                }
            }
        }
        return globs
    }
}
