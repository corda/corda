package net.corda.attachmentdemo.api

import net.corda.core.contracts.TransactionType
import net.corda.core.crypto.SecureHash
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.ApiUtils
import net.corda.core.utilities.Emoji
import net.corda.core.utilities.loggerFor
import net.corda.flows.FinalityFlow
import net.corda.testing.ALICE_KEY
import java.util.concurrent.CompletableFuture
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import kotlin.test.assertEquals

@Path("attachmentdemo")
class AttachmentDemoApi(val rpc: CordaRPCOps) {
    private val utils = ApiUtils(rpc)

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
            if (!rpc.attachmentExists(PROSPECTUS_HASH)) {
                javaClass.classLoader.getResourceAsStream("bank-of-london-cp.jar").use {
                    val id = rpc.uploadAttachment(it)
                    assertEquals(PROSPECTUS_HASH, id)
                }
            }

            // Create a trivial transaction that just passes across the attachment - in normal cases there would be
            // inputs, outputs and commands that refer to this attachment.
            val ptx = TransactionType.General.Builder(notary = null)
            require(rpc.attachmentExists(PROSPECTUS_HASH))
            ptx.addAttachment(PROSPECTUS_HASH)

            // Despite not having any states, we have to have at least one signature on the transaction
            ptx.signWith(ALICE_KEY)

            // Send the transaction to the other recipient
            val tx = ptx.toSignedTransaction()
            val protocolHandle = rpc.startFlow(::FinalityFlow, tx, setOf(it))
            protocolHandle.returnValue.toBlocking().first()

            Response.accepted().build()
        }
    }

    @POST
    @Path("await-transaction")
    @Consumes(MediaType.APPLICATION_JSON)
    fun runRecipient(): Response {
        val future = CompletableFuture<Response>()
        // Normally we would receive the transaction from a more specific flow, but in this case we let [FinalityFlow]
        // handle receiving it for us.
        rpc.verifiedTransactions().second.subscribe { event ->
            // When the transaction is received, it's passed through [ResolveTransactionsFlow], which first fetches any
            // attachments for us, then verifies the transaction. As such, by the time it hits the validated transaction store,
            // we have a copy of the attachment.
            val tx = event.tx
            val response = if (tx.attachments.isNotEmpty()) {
                assertEquals(PROSPECTUS_HASH, tx.attachments.first())
                require(rpc.attachmentExists(PROSPECTUS_HASH))

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
        val myInfo = rpc.nodeIdentity()
        val key = rpc.networkMapUpdates().first.first { it != myInfo }.legalIdentity.owningKey.toBase58String()
        return Response.ok().entity(key).build()
    }
}
