package net.corda.bridge.services.supervisors

import net.corda.bridge.services.api.*
import net.corda.bridge.services.artemis.BridgeArtemisConnectionServiceImpl
import net.corda.bridge.services.config.CryptoServiceFactory
import net.corda.bridge.services.filter.SimpleMessageFilterService
import net.corda.bridge.services.ha.ExternalMasterElectionService
import net.corda.bridge.services.ha.SingleInstanceMasterService
import net.corda.bridge.services.receiver.CryptoServiceSigningService
import net.corda.bridge.services.receiver.InProcessBridgeReceiverService
import net.corda.bridge.services.receiver.TunnelingBridgeReceiverService
import net.corda.bridge.services.sender.DirectBridgeSenderService
import net.corda.bridge.services.util.ServiceStateCombiner
import net.corda.bridge.services.util.ServiceStateHelper
import net.corda.core.utilities.contextLogger
import net.corda.nodeapi.internal.provider.DelegatedKeystoreProvider
import net.corda.nodeapi.internal.provider.extractCertificates
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import rx.Subscription
import java.security.Security

class BridgeSupervisorServiceImpl(conf: FirewallConfiguration,
                                  maxMessageSize: Int,
                                  auditService: FirewallAuditService,
                                  inProcessAMQPListenerService: BridgeAMQPListenerService?,
                                  private val stateHelper: ServiceStateHelper = ServiceStateHelper(log)) : BridgeSupervisorService, ServiceStateSupport by stateHelper {
    companion object {
        private val log = contextLogger()
        private val consoleLogger: Logger = LoggerFactory.getLogger("BasicInfo")
    }

    private val haService: BridgeMasterService
    private val artemisService: BridgeArtemisConnectionService
    private val senderService: BridgeSenderService
    private val receiverService: BridgeReceiverService
    private val filterService: IncomingMessageFilterService
    private val statusFollower: ServiceStateCombiner
    private var statusSubscriber: Subscription? = null
    private val signingService: TLSSigningService
    private val tunnelingSigningService: TLSSigningService
    private val artemisSigningService: TLSSigningService
    private val artemisKeystoreProvider: DelegatedKeystoreProvider

    init {
        val artemisSSlConfiguration = conf.outboundConfig?.artemisSSLConfiguration ?: conf.publicSSLConfiguration
        val artemisCryptoService = CryptoServiceFactory.get(conf.artemisCryptoServiceConfig, artemisSSlConfiguration.keyStore)
        artemisSigningService = CryptoServiceSigningService(artemisCryptoService,
                artemisSSlConfiguration.keyStore.get().extractCertificates(),
                artemisSSlConfiguration.trustStore.get(), conf.sslHandshakeTimeout, auditService)
        artemisKeystoreProvider = DelegatedKeystoreProvider(artemisSigningService)
        if (Security.getProvider( DelegatedKeystoreProvider.PROVIDER_NAME) == null) {
            Security.addProvider(artemisKeystoreProvider)
        }

        artemisService = BridgeArtemisConnectionServiceImpl(conf, maxMessageSize, auditService)
        haService = if (conf.haConfig == null) {
            SingleInstanceMasterService(conf, auditService)
        } else {
            ExternalMasterElectionService(conf, auditService, artemisService)
        }

        // TODO: get keystore public data from crypto service? or from config?
        val cryptoService = CryptoServiceFactory.get(conf.publicCryptoServiceConfig, conf.publicSSLConfiguration.keyStore)
        signingService = CryptoServiceSigningService(cryptoService, conf.publicSSLConfiguration.keyStore.get().extractCertificates(), conf.publicSSLConfiguration.trustStore.get(), conf.sslHandshakeTimeout, auditService)

        val controlLinkSSLConfiguration = conf.bridgeInnerConfig?.tunnelSSLConfiguration ?: conf.publicSSLConfiguration
        val tunnelingCryptoService = CryptoServiceFactory.get(conf.tunnelingCryptoServiceConfig, controlLinkSSLConfiguration.keyStore)
        tunnelingSigningService = CryptoServiceSigningService(tunnelingCryptoService, controlLinkSSLConfiguration.keyStore.get().extractCertificates(), controlLinkSSLConfiguration.trustStore.get(), auditService = auditService)
        senderService = DirectBridgeSenderService(conf, maxMessageSize, signingService, auditService, haService, artemisService)
        filterService = SimpleMessageFilterService(conf, auditService, artemisService, senderService)
        receiverService = if (conf.firewallMode == FirewallMode.SenderReceiver) {
            InProcessBridgeReceiverService(auditService, haService, signingService, inProcessAMQPListenerService!!, filterService)
        } else {
            require(inProcessAMQPListenerService == null) { "Should not have an in process instance of the AMQPListenerService" }
            TunnelingBridgeReceiverService(conf, auditService, haService, tunnelingSigningService, signingService, filterService)
        }
        statusFollower = ServiceStateCombiner(listOf(haService, senderService, receiverService, filterService))
        activeChange.subscribe({
            consoleLogger.info("BridgeSupervisorService: active = $it")
        }, { log.error("Error in state change", it) })
    }

    override fun start() {
        statusSubscriber = statusFollower.activeChange.subscribe({
            stateHelper.active = it
        }, { log.error("Error in state change", it) })
        artemisService.start()
        senderService.start()
        receiverService.start()
        filterService.start()
        haService.start()
        signingService.start()
        tunnelingSigningService.start()
        artemisSigningService.start()
    }

    override fun stop() {
        stateHelper.active = false
        haService.stop()
        senderService.stop()
        receiverService.stop()
        filterService.stop()
        artemisService.stop()
        statusSubscriber?.unsubscribe()
        statusSubscriber = null
        signingService.stop()
        tunnelingSigningService.stop()
        artemisSigningService.stop()
    }
}