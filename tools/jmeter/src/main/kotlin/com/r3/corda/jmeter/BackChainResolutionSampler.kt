package com.r3.corda.jmeter

import com.r3.corda.enterprise.perftestcordapp.POUNDS
import com.r3.corda.enterprise.perftestcordapp.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import org.apache.jmeter.config.Argument
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.slf4j.Logger
import java.util.*

enum class BackchainOpCode(val op: Int) {
    NOP(0x00),
    SELF_ISSUE(0x01),
    TRANSFER(0x02),
    FINAL_PAYMENT(0x03)
}

enum class TransferNode(var node: Char) {
    NODE_A('A'),
    NODE_B('B');
    override fun toString() : String {
        return node.toString()
    }

    fun inverse() : TransferNode {
        return if (this == NODE_A) { NODE_B } else { NODE_A }
    }
}

fun transferNodeFromBoolean(isNodeB: Boolean): TransferNode {
    return if (isNodeB) { TransferNode.NODE_B } else { TransferNode.NODE_A }
}

data class BackchainOperation(val opCode: BackchainOpCode, val value: Int, val fromNode: TransferNode, val testInfo: String)

/**
 * A sampler that creates cash iexn node A then moves it all to node B in units of 2. Once all the money is moved
 * it gets moved back from B to A in units of 4. It then gets moved from A to B in units of 8. Each round has
 * half the number of moves of twice the size of unit until the final move is for the entire issued amount.
 * Once this is done, the entire balance is transferred from A (or B) to a new node C. This forces C to fetch
 * the entire back chain.
 */
class BackChainResolutionSampler : DualNodeBaseFlowSampler() {
    companion object JMeterProperties {
        val otherPartyA = Argument("otherPartyNameA", "", "<meta>", "The X500 name of the payer (A).")
        val otherPartyB = Argument("otherPartyNameB", "", "<meta>", "The X500 name of the payee (B).")
        val finalParty = Argument("finalPayParty", "", "<meta>", "The X500 name of the final payee (C).")
        val coinSelection = Argument("useCoinSelection-NOT-IMPLEMENTED", "false", "<meta>", "True to use coin selection and false (or anything else) to avoid coin selection.")
        val anonymousIdentities = Argument("anonymousIdentities", "false", "<meta>", "True to use anonymous identities and false (or anything else) to use well known identities.")
        val initialAmount = Argument("initialAmount", "2", "<meta>", "The initial amount of currency to be transferred in unit sized blocks.")
        val initialUnitSize = Argument("initialUnitSize", "2", "<meta>", "The initial unit size for transfers.")
        val sendFinalPaymentFromB = Argument("sendFinalPaymentFromB", "false", "<meta>", "True to send final payment from B to C (else A pays C).")
    }

    lateinit var counterPartyToA: Party
    lateinit var counterPartyToB: Party
    lateinit var counterPartyFinal: Party
    var useCoinSelection: Boolean = true
    var useAnonymousIdentities: Boolean = true
    var payCfromNodeB: Boolean = false
    var initStartAmount: Int = 16
    var initUnitSize : Int = 2
    var operations = LinkedList<BackchainOperation>()
    var invocationCount : Int = 0

    /**
     * Work out if we need to start with node A of B such that the final payment is from the correct nodes based on payFromB
     * returns true if we need to start by issuing to node B rather than A.
     */
    fun determineStartNode(amount: Int, payFromNode: TransferNode, startUnitSize: Int) : TransferNode {
        var unitSize = startUnitSize
        var round: Int = 0
        var currentFromNode = TransferNode.NODE_A

        log.info("determineStartNode(startAmt=$amount, payNode=${payFromNode.toString()}, startUnitSize=$startUnitSize): Will see where we end up starting with 'A'.")
        while (amount > 0 && unitSize > 0) {
            val count = amount / unitSize
            log.info("determineStartNode(): Round=$round ('${currentFromNode.toString()}' => '${currentFromNode.inverse().toString()}'): size=$unitSize, tran_count=$count")
            currentFromNode = currentFromNode.inverse()
            if (unitSize >= amount) {
                break
            } else {
                round++
                unitSize *= 2 // Double the size we transfer
            }
        } // outer loop

        log.info("determineStartNode(): If we start from 'A', I conclude the final payment to 'C' would be from node '${currentFromNode.toString()}'")
        var ret = TransferNode.NODE_A
        if (payFromNode == currentFromNode) {
            log.info("determineStartNode(): NO inversion is required, we can issue to node 'A' to start with, to pay 'C' from node '${payFromNode.toString()}'.")
        } else {
            log.info("determineStartNode(): Inversion is required, we need to issue to node 'B' to start with, to pay 'C' from node '${payFromNode.toString()}'.")
            ret = ret.inverse()
        }
        return ret
    }

    override fun setupTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
        if (operations.size > 0) {
            log.info("Second call of BackChainResolutionSampler.setupTest(): Ignoring.")
            return
        }

        getNotaryIdentity(rpcProxy, testContext)
        counterPartyToA = getIdentity(rpcProxy, testContext, otherPartyA)
        counterPartyToB = getIdentity(rpcProxy, testContext, otherPartyB)
        counterPartyFinal = getIdentity(rpcProxy, testContext, finalParty)
        useCoinSelection = testContext.getParameter(coinSelection.name, coinSelection.value).toBoolean()
        initStartAmount = testContext.getParameter(initialAmount.name, initialAmount.value).toInt()
        initUnitSize = testContext.getParameter(initialUnitSize.name, initialUnitSize.value).toInt()
        useAnonymousIdentities = testContext.getParameter(anonymousIdentities.name, anonymousIdentities.value).toBoolean()
        payCfromNodeB = testContext.getParameter(sendFinalPaymentFromB.name, sendFinalPaymentFromB.value).toBoolean()

        // Create all the operations needed to complete the test
        log.info("Setting up test operations (start)...")

        // The process is as follows: Issue an amount to node A, transfer from A to B in units of 2 until all spent.
        // Then transfer back to A in double the size units (4 initially) then back to B in units of 8 etc until we have
        // one block transferred. Then we transfer the full amount to node C which forces the entire back chain to be
        // fetched for validation.

        // Create operation for issue of initial cash
        val payFromNode = transferNodeFromBoolean(payCfromNodeB)
        val startNode = determineStartNode(initStartAmount, payFromNode, initUnitSize)
        val startNodeNm = startNode.toString()
        log.info("START NODE (for issuance) = '$startNodeNm'")

        val startTestInfo = "[0] $startNodeNm issues $startNodeNm $initStartAmount GBP"
        val issOp = BackchainOperation(BackchainOpCode.SELF_ISSUE, initStartAmount, startNode, startTestInfo)
        val issStr: String = issOp.toString()
        log.info("OP Create: Issuing initial amount: [$issStr]")
        operations.add(issOp)

        var quantityRemaining: Int = initStartAmount
        var unitSize = initUnitSize
        var totalTransferred: Int = 0
        var fromNode = startNode
        var xferRound: Int = 0
        if (initStartAmount < 1 && initUnitSize < 1) {
            log.info("Will not allocate transfers (either initial amount ($initStartAmount) or unit size ($initUnitSize) is zero).")
            log.info("**** TOTAL NUMBER OF TEST ITERATIONS REQUIRED IS ZERO ****")
            return
        } else {
            log.info("Starting transfer Op main loop: amount = $initStartAmount, unit size = $initUnitSize")
        }

        // TODO: Check parameter consistency
        while (initStartAmount > 0 && initUnitSize > 0) {
            // Outer loop is to keep transferring back and forth until one transfer amount complete the transaction
            log.info("Round [$xferRound]: Starting Op generation for transfers from '${fromNode.toString()}' to '${fromNode.inverse().toString()}': Total to transfer: $quantityRemaining, unit size: $unitSize")
            var index = 0
            var xferOpStr: String = ""
            var finalAmtStr: String = ""
            while (quantityRemaining > 0) {
                val transferAmount = if (quantityRemaining < unitSize) { quantityRemaining } else { unitSize }
                val xrpo = xferRound + 1
                val testInfo = "[$xrpo] ${fromNode.toString()} pays ${fromNode.inverse().toString()} $transferAmount GBP"
                if (transferAmount != unitSize) {
                    log.info("Round [$xferRound]: Final transfer for round reduced to $transferAmount (from $unitSize) as initial amount is not 2^n")
                    finalAmtStr = BackchainOperation(BackchainOpCode.TRANSFER, transferAmount, fromNode, testInfo).toString()
                }

                quantityRemaining -= transferAmount
                totalTransferred += transferAmount
                log.trace { "Round [$xferRound]: Index[$index]: Generating transfer (${fromNode.toString()}->${fromNode.inverse().toString()}): xfer_amt=$transferAmount, total=$totalTransferred, remaining=$quantityRemaining" }

                val xferOp = BackchainOperation(BackchainOpCode.TRANSFER, transferAmount, fromNode, testInfo)
                if (index == 0) {
                    xferOpStr = xferOp.toString()
                }

                operations.add(xferOp)
                index++
            }

            if (finalAmtStr.isBlank()) {
                if (index == 1) {
                    log.trace { "OP Create: Generated [$xferOpStr]" }
                } else {
                    log.trace { "OP Create: Generated [$xferOpStr] X $index times." }
                }
            } else {
                val tims = index - 1
                if (tims == 1) {
                    log.trace { "OP Create: Generated [$xferOpStr]" }
                } else if (tims != 0) {
                    log.trace { "OP Create: Generated [$xferOpStr] X $tims times." }
                }
                log.trace { "OP Create: Generated [$finalAmtStr]" }
            }

            // Reverse the direction of transfer for the next round
            fromNode = fromNode.inverse()

            if (index <= 1) {
                // Once we hit the point where a single transfer moves all tne money, then the set up is complete
                log.info("Round[$xferRound] Total amount transferred (in blocks of $unitSize) is $totalTransferred, num transfer Ops for round = $index")
                log.info("Finishing main transfer loop; all transfers operations generated.")
                break
            } else {
                // Reset the transfer parameters and and double the unit size
                quantityRemaining = totalTransferred
                totalTransferred = 0
                xferRound++
                unitSize *= 2 // Double the size we transfer
            }
        } // outer loop

        log.trace { "Cash balance of $totalTransferred now sits in node ${fromNode.toString()}" }

        // Create the final move to force the backchain to be fetched...
        val xrpt = xferRound + 2
        val finalTestInfo = "[$xrpt] ${fromNode.toString()} pays C $totalTransferred GBP"
        val finalOp = BackchainOperation(BackchainOpCode.FINAL_PAYMENT, initStartAmount, fromNode, finalTestInfo)
        val finalStr: String = finalOp.toString()
        log.trace { "OP Create: Final transfer operation: [$finalStr]" }
        operations.add(finalOp)

        val sz = operations.size
        log.trace { "Setting up test operations (end)." }
        log.trace("Dump of created operations (count = ${operations.size}):")
        var idx: Int = 0
        val iter = operations.iterator()
        for (op in iter) {
            val opStr: String = op.toString()
            log.trace { "[$idx]: OP: [$opStr]" }
            idx++
        }
        log.info("**** TOTAL NUMBER OF TEST ITERATIONS REQUIRED IS $sz ****")
    }

    override fun determineNextOpTarget(): TransferNode {
        if (operations.size >= 1) {
            return operations.first().fromNode
        }

        log.error("determineNextOpTarget(): Called with no remaining items in operation list.")
        return TransferNode.NODE_A
    }

    override fun getTestLabel(): String {
        if (operations.size >= 1) {
            return operations.first().testInfo
        }

        log.error("getTestLabel(): Called with no remaining items in operation list.")
        return "Unknown Test"
    }

    override fun createFlowInvoke(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext): BaseFlowSampler.FlowInvoke<*> {
        if (operations.size < 1) {
            var ex = Exception("Too many iterations at max index [$invocationCount], ran off the end of operations to perform, adjust 'Thread Group': Loop Count to $invocationCount")
            log.error(ex.toString())
            throw ex
        }

        val op = operations.remove()
        val opStr: String = op.toString()
        log.info("createFlowInvoke(index=$invocationCount): operation=[$opStr], useCoinSelection=${useCoinSelection}")
        invocationCount++

        val amount = op.value.POUNDS
        when (op.opCode) {
            BackchainOpCode.SELF_ISSUE -> {
                log.info("ISSUE-OPERATION: $opStr")
                return BaseFlowSampler.FlowInvoke<CashIssueFlow>(CashIssueFlow::class.java, arrayOf(amount, OpaqueBytes.of(1), notaryIdentity))
            }

            BackchainOpCode.TRANSFER -> {
                log.info("TRANSFER-OPERATION: $opStr")
                val recipient = if (op.fromNode == TransferNode.NODE_A) { counterPartyToA } else if (op.fromNode == TransferNode.NODE_B) { counterPartyToB } else { counterPartyFinal }
                if (useCoinSelection) {
                    log.error("No CashPaymentFlow with selection - NOT IMPLEMENTED")
                    return BaseFlowSampler.FlowInvoke<CashPaymentFlow>(CashPaymentFlow::class.java, arrayOf(amount, recipient, useAnonymousIdentities))
                } else {
                    return BaseFlowSampler.FlowInvoke<CashPaymentFlow>(CashPaymentFlow::class.java, arrayOf(amount, recipient, useAnonymousIdentities))
                }
            }

            BackchainOpCode.FINAL_PAYMENT -> {
                val recipient = counterPartyFinal
                log.info("FINAL-PAYMENT-OPERATION: $opStr")
                if (useCoinSelection) {
                    log.error("No CashPaymentFlow with selection - NOT IMPLEMENTED")
                    return BaseFlowSampler.FlowInvoke<CashPaymentFlow>(CashPaymentFlow::class.java, arrayOf(amount, recipient, useAnonymousIdentities))
                } else {
                    return BaseFlowSampler.FlowInvoke<CashPaymentFlow>(CashPaymentFlow::class.java, arrayOf(amount, recipient, useAnonymousIdentities))
                }
            }

            else -> {
                throw Exception("Illegal operation code [$op.opCode]")
            }
        }
    }

    override fun teardownTest(rpcProxy: CordaRPCOps, testContext: JavaSamplerContext) {
    }

    override val additionalArgs: Set<Argument>
        get() = setOf(notary, otherPartyA, otherPartyB, finalParty, coinSelection, anonymousIdentities, initialAmount, initialUnitSize, sendFinalPaymentFromB)
}

