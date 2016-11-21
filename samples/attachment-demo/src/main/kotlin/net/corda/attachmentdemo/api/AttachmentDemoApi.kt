package net.corda.attachmentdemo.api

import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.SecureHash
import net.corda.core.failure
import net.corda.core.node.ServiceHub
import net.corda.core.success
import net.corda.core.utilities.ApiUtils
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.protocols.FinalityProtocol
import net.corda.testing.ALICE_KEY
import java.util.concurrent.CompletableFuture
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

@Path("attachmentdemo")
class AttachmentDemoApi(val services: ServiceHub) {
    private val utils = ApiUtils(services)

    private companion object {
        val PROSPECTUS_HASH = SecureHash.parse("decd098666b9657314870e192ced0c3519c2c9d395507a238338f8d003929de9")
        val logger = loggerFor<AttachmentDemoApi>()
    }

    @POST
    @Path("{party}/send")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runSender(@PathParam("party") partyKey: String): Response {
        return utils.withParty(partyKey) {
            // Make sure we have the file in storage
            // TODO: We should have our own demo file, not share the trader demo file
            if (services.storageService.attachments.openAttachment(PROSPECTUS_HASH) == null) {
                javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                    val id = services.storageService.attachments.importAttachment(it)
                    assertEquals(PROSPECTUS_HASH, id)
                }
            }

            // Create a trivial transaction that just passes across the attachment - in normal cases there would be
            // inputs, outputs and commands that refer to this attachment.
            val ptx = TransactionType.General.Builder(notary = null)
            ptx.addAttachment(services.storageService.attachments.openAttachment(PROSPECTUS_HASH)!!.id)

            // Despite not having any states, we have to have at least one signature on the transaction
            ptx.signWith(ALICE_KEY)

            // Send the transaction to the other recipient
            val tx = ptx.toSignedTransaction()
            services.invokeProtocolAsync<Unit>(FinalityProtocol::class.java, tx, setOf(it)).resultFuture.success {
                println("Successfully sent attachment with the FinalityProtocol")
            }.failure {
                logger.error("Failed to send attachment with the FinalityProtocol")
            }

            Response.accepted().build()
        }
    }

    @POST
    @Path("await-transaction")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRecipient(): Response {
        val future = CompletableFuture<Response>()
        // Normally we would receive the transaction from a more specific protocol, but in this case we let [FinalityProtocol]
        // handle receiving it for us.
        services.storageService.validatedTransactions.updates.subscribe { event ->
            // When the transaction is received, it's passed through [ResolveTransactionsProtocol], which first fetches any
            // attachments for us, then verifies the transaction. As such, by the time it hits the validated transaction store,
            // we have a copy of the attachment.
            val tx = event.tx
            val response = if (tx.attachments.isNotEmpty()) {
                val attachment = services.storageService.attachments.openAttachment(tx.attachments.first())
                assertEquals(PROSPECTUS_HASH, attachment?.id)

                println("File received - we're happy!\n\nFinal transaction is:\n\n${Emoji.renderIfSupported(event.tx)}")
                Response.ok().entity("Final transaction is: ${Emoji.renderIfSupported(event.tx)}").build()
            } else {
                Response.serverError().entity("No attachments passed").build()
            }
            future.complete(response)
        }

        return future.get()
    }

    /**
     * Gets details of the other side. To be removed when identity API is added.
     */
    @GET
    @Path("other-side-key")
    @Produces(MediaType.APPLICATION_JSON)
    fun getOtherSide(): Response? {
        val key = services.networkMapCache.partyNodes.first { it != services.myInfo }.legalIdentity.owningKey.toBase58String()
        return Response.ok().entity(key).build()
    }
}
