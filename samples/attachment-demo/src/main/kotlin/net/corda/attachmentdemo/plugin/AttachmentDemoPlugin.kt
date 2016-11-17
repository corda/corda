package net.corda.attachmentdemo.plugin

import net.corda.attachmentdemo.api.AttachmentDemoApi
import net.corda.core.node.CordaPluginRegistry
import net.corda.core.transactions.SignedTransaction
import net.corda.protocols.FinalityProtocol

class AttachmentDemoPlugin : CordaPluginRegistry() {
    // A list of classes that expose web APIs.
    override val webApis: List<Class<*>> = listOf(AttachmentDemoApi::class.java)
    // A list of protocols that are required for this cordapp
    override val requiredProtocols: Map<String, Set<String>> = mapOf(
        FinalityProtocol::class.java.name to setOf(SignedTransaction::class.java.name, setOf(Unit).javaClass.name, setOf(Unit).javaClass.name)
    )
    override val servicePlugins: List<Class<*>> = listOf()
}
