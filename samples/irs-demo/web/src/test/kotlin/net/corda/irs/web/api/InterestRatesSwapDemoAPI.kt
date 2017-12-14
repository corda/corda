package net.corda.irs.web.api

import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.irs.api.NodeInterestRates
import net.corda.irs.flows.UpdateBusinessDayFlow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * GET /api/irs/demodate - return the current date as viewed by the system in YYYY-MM-DD format.
 * PUT /api/irs/demodate - put date in format YYYY-MM-DD to advance the current date as viewed by the system and
 * POST /api/irs/fixes - store the fixing data as a text file
 */
@RestController
@RequestMapping("/api/irs")
class InterestRatesSwapDemoAPI {
    companion object {
        private val logger = contextLogger()
    }

    @Autowired
    lateinit var rpc: CordaRPCOps

    @PutMapping("demodate")
    fun storeDemoDate(@RequestBody newDemoDate: LocalDate): ResponseEntity<Any?> {
        val priorDemoDate = fetchDemoDate()
        // Can only move date forwards
        if (newDemoDate.isAfter(priorDemoDate)) {
            rpc.startFlow(UpdateBusinessDayFlow::Broadcast, newDemoDate).returnValue.getOrThrow()
            return ResponseEntity.ok().build()
        }
        val msg = "demodate is already $priorDemoDate and can only be updated with a later date"
        logger.error("Attempt to set demodate to $newDemoDate but $msg")
        return ResponseEntity.status(HttpStatus.CONFLICT).body(msg)
    }

    @GetMapping("demodate")
    fun fetchDemoDate(): LocalDate {
        return LocalDateTime.ofInstant(rpc.currentNodeTime(), ZoneId.systemDefault()).toLocalDate()
    }

    @PostMapping("fixes")
    fun storeFixes(@RequestBody file: String): ResponseEntity<Any?> {
        rpc.startFlow(NodeInterestRates::UploadFixesFlow, file).returnValue.getOrThrow()
        return ResponseEntity.ok().build()
    }
}