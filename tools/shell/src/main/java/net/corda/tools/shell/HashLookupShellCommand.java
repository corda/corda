package net.corda.tools.shell;

import net.corda.core.crypto.SecureHash;
import net.corda.core.internal.VisibleForTesting;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.messaging.StateMachineTransactionMapping;
import org.crsh.cli.Argument;
import org.crsh.cli.Command;
import org.crsh.cli.Man;
import org.crsh.cli.Named;
import org.crsh.cli.Usage;
import org.crsh.text.Color;
import org.crsh.text.Decoration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;

@Named("hashLookup")
public class HashLookupShellCommand extends CordaRpcOpsShellCommand {
    private static Logger logger = LoggerFactory.getLogger(HashLookupShellCommand.class);
    private static final String manualText ="Checks if a transaction matching a specified Id hash value is recorded on this node.\n\n" +
            "Both the transaction Id and the hashed value of a transaction Id (as returned by the Notary in case of a double-spend) is a valid input.\n" +
            "This is mainly intended to be used for troubleshooting notarisation issues when a\n" +
            "state is claimed to be already consumed by another transaction.\n\n" +
            "Example usage: hashLookup E470FD8A6350A74217B0A99EA5FB71F091C84C64AD0DE0E72ECC10421D03AAC9";

    @Command
    @Man(manualText)

    public void main(@Usage("A transaction Id or a hexadecimal SHA-256 hash value representing the hashed transaction Id") @Argument(unquote = false) String txIdHash) {
        CordaRPCOps proxy = ops();
        try {
            hashLookup(out, proxy, txIdHash);
        } catch (IllegalArgumentException ex) {
            out.println(manualText);
            out.println(ex.getMessage(), Decoration.bold, Color.red);
        }
    }

    @VisibleForTesting
    protected static void hashLookup(PrintWriter out, CordaRPCOps proxy, String txIdHash) throws IllegalArgumentException {
        logger.info("Executing command \"hashLookup\".");

        if (txIdHash == null) {
            out.println(manualText);
            throw new IllegalArgumentException("Please provide a hexadecimal transaction Id hash value or a transaction Id");
        }

        SecureHash txIdHashParsed;
        try {
            txIdHashParsed = SecureHash.parse(txIdHash);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The provided string is not a valid hexadecimal SHA-256 hash value");
        }

        List<StateMachineTransactionMapping> mapping = proxy.stateMachineRecordedTransactionMappingSnapshot();
        Optional<SecureHash> match = mapping.stream()
                .map(StateMachineTransactionMapping::getTransactionId)
                .filter(
                        txId -> txId.equals(txIdHashParsed) || SecureHash.sha256(txId.getBytes()).equals(txIdHashParsed)
                )
                .findFirst();

        if (match.isPresent()) {
            SecureHash found = match.get();
            out.println("Found a matching transaction with Id: " + found.toString());
        } else {
            throw new IllegalArgumentException("No matching transaction found");
        }
    }
}
