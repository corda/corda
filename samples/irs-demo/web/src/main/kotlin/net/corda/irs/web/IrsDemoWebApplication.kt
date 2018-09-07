package net.corda.irs.web

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.client.jackson.JacksonSupport
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.finance.plugin.registerFinanceJSONMappers
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

/**
 * Simple and sample SpringBoot web application which communicates with Corda node using RPC.
 * [CordaRPCOps] instance can be managed simply as plain Spring bean.
 * If support for (de)serializatin of Corda classes is required, [ObjectMapper] can be configured using helper
 * functions, see [objectMapper]
 */
@SpringBootApplication
class IrsDemoWebApplication {
    @Value("\${corda.host}")
    lateinit var cordaHost:String

    @Value("\${corda.user}")
    lateinit var cordaUser:String

    @Value("\${corda.password}")
    lateinit var cordaPassword:String

    @Bean
    fun rpcClient(): CordaRPCOps {
        log.info("Connecting to Corda on $cordaHost using username $cordaUser and password $cordaPassword")
        // TODO remove this when CordaRPC gets proper connection retry, please
        var maxRetries = 100;
        do {
            try {
                return CordaRPCClient(NetworkHostAndPort.parse(cordaHost)).start(cordaUser, cordaPassword).proxy
            } catch (ex: RPCException) {
                if (maxRetries-- > 0) {
                    Thread.sleep(1000)
                } else {
                    throw ex
                }
            }
        } while (true)
    }

    @Bean
    fun objectMapper(@Autowired cordaRPCOps: CordaRPCOps): ObjectMapper {
        val mapper = JacksonSupport.createDefaultMapper(cordaRPCOps)
        registerFinanceJSONMappers(mapper)
        return mapper
    }

    // running as standalone java app
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        @JvmStatic fun main(args: Array<String>) {
            SpringApplication.run(IrsDemoWebApplication::class.java, *args)
        }
    }
}

