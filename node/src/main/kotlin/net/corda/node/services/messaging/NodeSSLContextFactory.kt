package net.corda.node.services.messaging

import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import net.corda.nodeapi.internal.ArtemisTcpTransport.Companion.TRUST_MANAGER_FACTORY_NAME
import net.corda.nodeapi.internal.protonwrapper.netty.createAndInitSslContext
import org.apache.activemq.artemis.core.remoting.impl.ssl.DefaultOpenSSLContextFactory
import org.apache.activemq.artemis.core.remoting.impl.ssl.DefaultSSLContextFactory
import org.apache.activemq.artemis.spi.core.remoting.ssl.SSLContextConfig
import org.apache.activemq.artemis.utils.ClassloadingUtil
import java.io.File
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.security.AccessController
import java.security.KeyStore
import java.security.PrivilegedAction
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class NodeSSLContextFactory : DefaultSSLContextFactory() {
    override fun getSSLContext(config: SSLContextConfig, additionalOpts: Map<String, Any>): SSLContext {
        val trustManagerFactory = additionalOpts[TRUST_MANAGER_FACTORY_NAME] as TrustManagerFactory?
        return if (trustManagerFactory != null) {
            createAndInitSslContext(loadKeyManagerFactory(config), trustManagerFactory)
        } else {
            super.getSSLContext(config, additionalOpts)
        }
    }

    override fun getPriority(): Int {
        // We make sure this factory is the one that's chosen, so any sufficiently large value will do.
        return 15
    }
}


class NodeOpenSSLContextFactory : DefaultOpenSSLContextFactory() {
    override fun getServerSslContext(config: SSLContextConfig, additionalOpts: Map<String, Any>): SslContext {
        val trustManagerFactory = additionalOpts[TRUST_MANAGER_FACTORY_NAME] as TrustManagerFactory?
        return if (trustManagerFactory != null) {
            SslContextBuilder
                    .forServer(loadKeyManagerFactory(config))
                    .sslProvider(SslProvider.OPENSSL)
                    .trustManager(trustManagerFactory)
                    .build()
        } else {
            super.getServerSslContext(config, additionalOpts)
        }
    }

    override fun getPriority(): Int {
        // We make sure this factory is the one that's chosen, so any sufficiently large value will do.
        return 15
    }
}


private fun loadKeyManagerFactory(config: SSLContextConfig): KeyManagerFactory {
    val keyStore = loadKeystore(config.keystoreProvider, config.keystoreType, config.keystorePath, config.keystorePassword)
    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
    keyManagerFactory.init(keyStore, config.keystorePassword?.toCharArray())
    return keyManagerFactory
}


/**
 * This is a copy of [SSLSupport.loadKeystore] so that we can use the [NodeSSLContextFactory] and
 * maintain keystore loading with the correct [keystoreProvider].
 */
private fun loadKeystore(
        keystoreProvider: String?,
        keystoreType: String,
        keystorePath: String?,
        keystorePassword: String?
) : KeyStore {
    val keyStore = keystoreProvider?.let { KeyStore.getInstance(keystoreType, it) } ?: KeyStore.getInstance(keystoreType)
    var inputStream : InputStream? = null
    try {
        if (keystorePath != null && keystorePath.isNotEmpty()) {
            val keystoreURL = validateStoreURL(keystorePath)
            inputStream = keystoreURL.openStream()
        }
        keyStore.load(inputStream, keystorePassword?.toCharArray())
    } finally {
        inputStream?.closeQuietly()
    }
    return keyStore
}


/**
 * This is a copy of [SSLSupport.validateStoreURL] so we can have a full copy of
 * [SSLSupport.loadKeystore].
 */
private fun validateStoreURL(storePath: String): URL {
    return try {
        URL(storePath)
    } catch (e: MalformedURLException) {
        val file = File(storePath)
        if (file.exists() && file.isFile) {
            file.toURI().toURL()
        } else {
            findResource(storePath)
        }
    }
}


/**
 * This is a copy of [SSLSupport.findResource] so we can have a full copy of
 * [SSLSupport.validateStoreURL] and.
 */
private fun findResource(resourceName: String): URL {
    return AccessController.doPrivileged(PrivilegedAction {
        ClassloadingUtil.findResource(resourceName)
    })
}


/**
 * This is an inline function for [InputStream] so it can be closed and
 * ignore an exception.
 */
private fun InputStream?.closeQuietly() {
    try {
        this?.close()
    }
    catch ( ex : Exception ) {
        // quietly absorb problems
    }
}

