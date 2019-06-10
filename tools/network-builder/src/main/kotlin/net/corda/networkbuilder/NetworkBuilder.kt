package net.corda.networkbuilder

import net.corda.networkbuilder.backends.Backend
import net.corda.networkbuilder.context.Context
import net.corda.networkbuilder.nodes.*
import net.corda.networkbuilder.notaries.NotaryCopier
import net.corda.networkbuilder.notaries.NotaryFinder
import net.corda.node.utilities.NamedThreadFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

interface NetworkBuilder {

    companion object {
        fun instance(): NetworkBuilder {
            return NetworkBuilderImpl()
        }
    }

    fun onNodeLocated(callback: (FoundNode) -> Unit): NetworkBuilder
    fun onNodeCopied(callback: (CopiedNode) -> Unit): NetworkBuilder
    fun onNodeBuild(callback: (BuiltNode) -> Unit): NetworkBuilder
    fun onNodePushed(callback: (PushedNode) -> Unit): NetworkBuilder
    fun onNodeInstance(callback: (NodeInstance) -> Unit): NetworkBuilder

    fun withNetworkName(networtName: String): NetworkBuilder
    fun withBasedir(baseDir: File): NetworkBuilder
    fun withBackend(backendType: Backend.BackendType): NetworkBuilder
    fun withBackendOptions(options: Map<String, String>): NetworkBuilder

    fun build(): CompletableFuture<Pair<List<NodeInstance>, Context>>
    fun onNodeStartBuild(callback: (FoundNode) -> Unit): NetworkBuilder
    fun onNodePushStart(callback: (BuiltNode) -> Unit): NetworkBuilder
    fun onNodeInstancesRequested(callback: (List<NodeInstanceRequest>) -> Unit): NetworkBuilder
}

private class NetworkBuilderImpl : NetworkBuilder {

    @Volatile
    private var onNodeLocatedCallback: ((FoundNode) -> Unit) = {}
    @Volatile
    private var onNodeCopiedCallback: ((CopiedNode) -> Unit) = {}
    @Volatile
    private var onNodeBuildStartCallback: (FoundNode) -> Unit = {}
    @Volatile
    private var onNodeBuiltCallback: ((BuiltNode) -> Unit) = {}
    @Volatile
    private var onNodePushStartCallback: ((BuiltNode) -> Unit) = {}
    @Volatile
    private var onNodePushedCallback: ((PushedNode) -> Unit) = {}
    @Volatile
    private var onNodeInstanceRequestedCallback: (List<NodeInstanceRequest>) -> Unit = {}
    @Volatile
    private var onNodeInstanceCallback: ((NodeInstance) -> Unit) = {}

    @Volatile
    private var nodeCounts = mapOf<String, Int>()
    @Volatile
    private lateinit var networkName: String
    @Volatile
    private var workingDir: File? = null
    private val cacheDirName = Constants.BOOTSTRAPPER_DIR_NAME
    @Volatile
    private var backendType = Backend.BackendType.LOCAL_DOCKER
    @Volatile
    private var backendOptions: Map<String, String> = mapOf()

    override fun onNodeLocated(callback: (FoundNode) -> Unit): NetworkBuilder {
        this.onNodeLocatedCallback = callback
        return this
    }

    override fun onNodeCopied(callback: (CopiedNode) -> Unit): NetworkBuilder {
        this.onNodeCopiedCallback = callback
        return this
    }

    override fun onNodeStartBuild(callback: (FoundNode) -> Unit): NetworkBuilder {
        this.onNodeBuildStartCallback = callback
        return this;
    }

    override fun onNodeBuild(callback: (BuiltNode) -> Unit): NetworkBuilder {
        this.onNodeBuiltCallback = callback
        return this
    }

    override fun onNodePushed(callback: (PushedNode) -> Unit): NetworkBuilder {
        this.onNodePushedCallback = callback
        return this
    }

    override fun onNodeInstancesRequested(callback: (List<NodeInstanceRequest>) -> Unit): NetworkBuilder {
        this.onNodeInstanceRequestedCallback = callback
        return this
    }

    override fun onNodeInstance(callback: (NodeInstance) -> Unit): NetworkBuilder {
        this.onNodeInstanceCallback = callback;
        return this
    }

    override fun withNetworkName(networtName: String): NetworkBuilder {
        this.networkName = networtName
        return this
    }

    override fun withBasedir(baseDir: File): NetworkBuilder {
        this.workingDir = baseDir
        return this
    }

    override fun withBackend(backendType: Backend.BackendType): NetworkBuilder {
        this.backendType = backendType
        return this
    }

    override fun withBackendOptions(options: Map<String, String>): NetworkBuilder {
        this.backendOptions = HashMap(options)
        return this
    }

    override fun onNodePushStart(callback: (BuiltNode) -> Unit): NetworkBuilder {
        this.onNodePushStartCallback = callback;
        return this;
    }

    override fun build(): CompletableFuture<Pair<List<NodeInstance>, Context>> {

        val executor = Executors.newCachedThreadPool(NamedThreadFactory("network-builder"))

        val cacheDir = File(workingDir, cacheDirName)
        val baseDir = workingDir!!
        val context = Context(networkName, backendType, backendOptions)
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        val (containerPusher, instantiator, volume) = Backend.fromContext(context, cacheDir)
        val nodeFinder = NodeFinder(baseDir)
        val notaryFinder = NotaryFinder(baseDir)
        val notaryCopier = NotaryCopier(cacheDir)

        val nodeInstantiator = NodeInstantiator(instantiator, context)
        val nodeBuilder = NodeBuilder()
        val nodeCopier = NodeCopier(cacheDir)
        val nodePusher = NodePusher(containerPusher, context)

        val nodeDiscoveryFuture = CompletableFuture.supplyAsync {
            val foundNodes = nodeFinder.findNodes()
                    .map { it to nodeCounts.getOrDefault(it.name.toLowerCase(), 1) }
                    .toMap()
            foundNodes
        }

        val notaryDiscoveryFuture = CompletableFuture.supplyAsync {
            val copiedNotaries = notaryFinder.findNotaries()
                    .map { foundNode: FoundNode ->
                        onNodeBuildStartCallback.invoke(foundNode)
                        notaryCopier.copyNotary(foundNode)
                    }
            volume.notariesForNetworkParams(copiedNotaries)
            copiedNotaries
        }

        val notariesFuture = notaryDiscoveryFuture.thenCompose { copiedNotaries ->
            copiedNotaries
                    .map { copiedNotary ->
                        nodeBuilder.buildNode(copiedNotary).thenAlsoAsync {
                            onNodeBuildStartCallback.invoke(it)
                        }
                    }.map { builtNotaryFuture ->
                        builtNotaryFuture.thenComposeAsync { builtNotary ->
                            onNodeBuiltCallback(builtNotary)
                            onNodePushStartCallback(builtNotary)
                            nodePusher.pushNode(builtNotary).thenAlsoAsync { pushedNotary ->
                                onNodePushedCallback(pushedNotary)
                            }
                        }
                    }.map { pushedNotary ->
                        pushedNotary.thenApplyAsync {
                            nodeInstantiator.createInstanceRequest(it).also { onNodeInstanceRequestedCallback(listOf(it)) }
                        }
                    }.map { instanceRequest ->
                        instanceRequest.thenComposeAsync { request ->
                            nodeInstantiator.instantiateNotaryInstance(request).thenApply { it.also(onNodeInstanceCallback) }
                        }
                    }.toSingleFuture()
        }

        val nodesFuture = notaryDiscoveryFuture.thenCombineAsync(nodeDiscoveryFuture) { _, nodeCount ->
            nodeCount.keys
                    .map { foundNode ->
                        nodeCopier.copyNode(foundNode).let {
                            onNodeCopiedCallback.invoke(it)
                            it
                        }
                    }.map { copiedNode: CopiedNode ->
                        onNodeBuildStartCallback.invoke(copiedNode)
                        nodeBuilder.buildNode(copiedNode)
                    }.map { builtNodeFuture ->
                        builtNodeFuture.thenComposeAsync { builtNode ->
                            onNodeBuiltCallback.invoke(builtNode)
                            nodePusher.pushNode(builtNode).thenAlsoAsync { pushedNode ->
                                onNodePushedCallback.invoke(pushedNode)
                            }
                        }
                    }.map { pushedNodeFuture ->
                        pushedNodeFuture.thenApplyAsync {
                            nodeInstantiator.createInstanceRequests(it, nodeCount).also(onNodeInstanceRequestedCallback)

                        }
                    }.map { instanceRequests ->
                        instanceRequests.thenComposeAsync { requests ->
                            requests.map { request ->
                                nodeInstantiator.instantiateNodeInstance(request)
                                        .thenApplyAsync { nodeInstance ->
                                            context.registerNode(nodeInstance)
                                            onNodeInstanceCallback.invoke(nodeInstance)
                                            nodeInstance
                                        }
                            }.toSingleFuture()
                        }
                    }.toSingleFuture()
        }.thenCompose { it }.thenApplyAsync { it.flatten() }

        return notariesFuture.thenCombineAsync(nodesFuture) { _, nodeInstances ->
            context.networkInitiated = true
            nodeInstances to context
        }
    }
}

fun <T> List<CompletableFuture<T>>.toSingleFuture(): CompletableFuture<List<T>> {
    return CompletableFuture.allOf(*this.toTypedArray()).thenApplyAsync {
        this.map { it.getNow(null) }
    }
}

fun <T> CompletableFuture<T>.thenAlsoAsync(consumer: (T) -> Unit): CompletableFuture<T> {
    return this.thenApplyAsync {
        consumer(it)
        it
    }
}
