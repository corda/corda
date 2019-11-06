@file:JvmName("ComponentUtils")
package net.corda.node.internal.djvm

import net.corda.core.contracts.ComponentGroupEnum
import net.corda.core.crypto.componentHash
import net.corda.core.transactions.ComponentGroup
import net.corda.core.transactions.FilteredComponentGroup
import net.corda.djvm.rewiring.SandboxClassLoader
import net.corda.node.djvm.ComponentBuilder
import java.util.function.Function

class ComponentFactory(
    private val classLoader: SandboxClassLoader,
    private val taskFactory: Function<in Any, out Function<in Any?, out Any?>>,
    private val sandboxBasicInput: Function<in Any?, out Any?>,
    private val serializer: Serializer,
    private val componentGroups: List<ComponentGroup>
) {
    fun toSandbox(
        groupType: ComponentGroupEnum,
        clazz: Class<*>
    ): Any? {
        val components = (componentGroups.firstOrNull(groupType::isSameType) ?: return null).components
        val componentBytes = Array(components.size) { idx -> components[idx].bytes }
        return classLoader.createTaskFor(taskFactory, ComponentBuilder::class.java).apply(arrayOf(
            classLoader.createForImport(serializer.deserializerFor(clazz)),
            sandboxBasicInput.apply(groupType),
            componentBytes
        ))
    }

    fun calculateLeafIndicesFor(groupType: ComponentGroupEnum): IntArray? {
        val componentGroup = componentGroups.firstOrNull(groupType::isSameType) as? FilteredComponentGroup ?: return null
        val componentHashes = componentGroup.components.mapIndexed { index, component ->
            componentHash(componentGroup.nonces[index], component)
        }
        return componentHashes.map { componentGroup.partialMerkleTree.leafIndex(it) }.toIntArray()
    }
}

private fun ComponentGroupEnum.isSameType(group: ComponentGroup): Boolean {
    return group.groupIndex == ordinal
}
