/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.jmeter

import com.jcraft.jsch.Buffer
import com.jcraft.jsch.Identity
import com.jcraft.jsch.IdentityRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.agentproxy.AgentProxy
import com.jcraft.jsch.agentproxy.connector.PageantConnector
import com.jcraft.jsch.agentproxy.connector.SSHAgentConnector
import com.jcraft.jsch.agentproxy.usocket.JNAUSocketFactory
import org.slf4j.LoggerFactory
import java.util.*

private val log = LoggerFactory.getLogger(Ssh::class.java)

/**
 * Creates a new [JSch] instance with identities loaded from the running SSH agent.
 */
fun setupJSchWithSshAgent(): JSch {
    val connector =
            if (System.getenv("SSH_AUTH_SOCK") == null)
                PageantConnector()
            else
                SSHAgentConnector(JNAUSocketFactory())
    val agentProxy = AgentProxy(connector)
    val identities = agentProxy.identities
    require(identities.isNotEmpty()) { "No SSH identities found, please add one to the agent" }
    require(identities.size == 1) { "Multiple SSH identities found, don't know which one to pick" }
    val identity = identities[0]
    log.info("Using SSH identity ${String(identity.comment)}")

    return JSch().apply {
        identityRepository = object : IdentityRepository {
            override fun getStatus(): Int {
                if (connector.isAvailable) {
                    return IdentityRepository.RUNNING
                } else {
                    return IdentityRepository.UNAVAILABLE
                }
            }

            override fun getName() = connector.name
            override fun getIdentities(): Vector<Identity> = Vector(listOf(
                    object : Identity {
                        override fun clear() {}
                        override fun getAlgName() = String(Buffer(identity.blob).string)
                        override fun getName() = String(identity.comment)
                        override fun isEncrypted() = false
                        override fun getSignature(data: ByteArray?) = agentProxy.sign(identity.blob, data)
                        @Suppress("OverridingDeprecatedMember")
                        override fun decrypt() = true

                        override fun getPublicKeyBlob() = identity.blob
                        override fun setPassphrase(passphrase: ByteArray?) = true
                    }
            ))

            override fun remove(blob: ByteArray?) = throw UnsupportedOperationException()
            override fun removeAll() = throw UnsupportedOperationException()
            override fun add(bytes: ByteArray?) = throw UnsupportedOperationException()
        }
    }
}
