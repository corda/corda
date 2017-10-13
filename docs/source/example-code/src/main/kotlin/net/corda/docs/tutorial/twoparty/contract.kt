package net.corda.docs.tutorial.twoparty

import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.docs.java.tutorial.helloworld.IOUState

// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
fun main(args: Array<String>) {
    val command = Any() as Command<*>
    val out = Any() as IOUState
    requireThat {
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE


        // DOCSTART 01
        // Constraints on the signers.
        "There must be two signers." using (command.signers.toSet().size == 2)
        "The borrower and lender must be signers." using (command.signers.containsAll(listOf(
                out.borrower.owningKey, out.lender.owningKey)))
        // DOCEND 01


// START OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE
    }
}
// END OF BOILERPLATE FOR CODE FRAGMENT TO COMPILE